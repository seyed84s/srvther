package app.srvther.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.srvther.SrvtherApp
import app.srvther.MainActivity
import app.srvther.R
import app.srvther.core.SrvtherController
import app.srvther.core.SrvtherProcess
import app.srvther.core.Diagnostics
import app.srvther.core.DiagnosticsLog
import app.srvther.core.EngineMeta
import app.srvther.core.AutoCandidate
import app.srvther.core.PortProbe
import app.srvther.core.ProfileCodec
import app.srvther.core.HevTunnel
import app.srvther.core.RoutingEngine
import app.srvther.core.ShareBridge
import app.srvther.core.SmartAuto
import app.srvther.core.SocksTunBridge
import app.srvther.core.TunnelConfig
import app.srvther.model.ConnectionProfile
import app.srvther.model.ConnectionState
import app.srvther.model.Noize
import app.srvther.model.Protocol
import app.srvther.model.SplitMode
import app.srvther.widget.SrvtherWidgetProvider
import java.io.File

/**
 * The heart of the app. On connect it:
 *   1. launches the bundled `srvther` engine (opens SOCKS5 on 127.0.0.1:1819),
 *   2. waits until that port is actually reachable (ground-truth check),
 *   3. builds the VPN TUN interface,
 *   4. starts the embedded hev-socks5-tunnel core (libhev-socks5-tunnel.so) to forward all
 *      traffic through the proxy — replacing the need for v2rayNG entirely,
 *   5. supervises both processes and auto-reconnects on failure.
 */



class SrvtherVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null
    private var engine: SrvtherProcess? = null
    private var xrayController: libv2ray.CoreController? = null
    private var tunnelStarted: Boolean = false
    private var runJob: Job? = null

    /**
     * The teardown coroutine of the PREVIOUS session, if one is still
     * finishing. A new connect waits for it instead of racing it (1.2.2
     * protocol-switch fix).
     */
    private var stopJob: Job? = null

    /** Active userspace filter bridge (only when per-app blocking is on). */
    private var tunBridge: SocksTunBridge? = null

    /** Last profile the service ran with (kill-switch decisions). */
    private var lastProfile: ConnectionProfile? = null

    /** True while the kill-switch blackhole TUN is up. */
    @Volatile
    private var lockdownTunActive = false

    /** Consecutive failed watchdog probes (1.2.4 stability watchdog). */
    private var probeFailures = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                // STRICT KILL SWITCH (1.2.4): a manual disconnect must not
                // open a leak window. With strict mode on, the first
                // disconnect engages lockdown instead; disconnecting FROM
                // lockdown lifts it.
                val last = lastProfile
                when {
                    lockdownTunActive -> stopEverything()
                    last != null && last.strictKillSwitch -> enterLockdown(last)
                    else -> stopEverything()
                }
                return START_NOT_STICKY
            }
            else -> {
                val profile = ProfileCodec.decode(intent?.getStringExtra(EXTRA_PROFILE))
                startForeground(NOTIF_ID, buildNotification(getString(R.string.state_launching)))
                startTunnel(profile)
            }
        }
        return START_STICKY
    }

    private fun startTunnel(profile: ConnectionProfile) {
        lastProfile = profile
        // 1.2.2 PROTOCOL-SWITCH FIX: this used to bail out silently whenever a
        // previous run coroutine was still winding down ("if active, return"),
        // so a connect tapped right after a disconnect — or right after
        // switching protocol — was simply DROPPED. The user then waited,
        // tapped again, and the app looked like it took forever to start.
        // Now the new session takes ownership: it waits for the old one to
        // finish, tears its natives down, and only then launches the engine.
        val previousRun = runJob
        val previousStop = stopJob
        runJob = scope.launch {
            if (previousRun != null) {
                // Same ordering rule as the disconnect path: cancel, kill the
                // natives (which unblocks the old session immediately), and
                // only then wait for it to finish. Joining first would stall
                // the new connect for as long as the old session's engine wait
                // still had to run.
                previousRun.cancel()
                cleanupNativeOnly()
                previousRun.join()
            }
            previousStop?.join()
            try {
                connectFlow(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SrvtherController.setState(
                    ConnectionState.Error(e.message ?: getString(R.string.state_error)),
                )
                updateNotification(getString(R.string.state_error))
                cleanupNativeOnly()
            }
        }
    }

    private suspend fun connectFlow(profile: ConnectionProfile) {
        DiagnosticsLog.clear()
        // STALE-CIRCLES ROOT-CAUSE FIX: the four self-test circles were only
        // reset inside Diagnostics.run(), which starts AFTER the engine has
        // launched AND finished its endpoint scan — so on a reconnect the
        // previous session's green circles sat on screen for the entire scan
        // and appeared to "reset late". Reset them the INSTANT a new connect
        // starts, so the panel always reflects the current attempt on time.
        Diagnostics.resetChecks()
        EngineMeta.reset()
        DiagnosticsLog.i(TAG, "Connect requested — protocol=${profile.protocol} scan=${profile.scanMode} ip=${profile.ipVersion}")

        val resolved: ConnectionProfile =
            if (profile.protocol == Protocol.AUTO) {
                connectSmartAuto(profile)
            } else {
                // An explicitly chosen protocol keeps that protocol; the
                // engine still selects its own endpoint (see [directPlan]).
                SrvtherController.setState(ConnectionState.Launching)
                runLadder(directPlan(profile), getString(R.string.err_protocol_failed))
            }

        // Desktop-parity info row (1.2.4): publish the protocol that actually
        // won (Smart Auto resolves AUTO to a concrete protocol). The endpoint
        // arrives through EngineMeta's engine-log parser; for a pinned peer we
        // already know it here (no selection line is logged).
        EngineMeta.setProtocol(resolved.protocol.name)
        if (resolved.manualPeer.isNotBlank()) EngineMeta.setEndpoint(resolved.manualPeer)

        SrvtherController.setState(ConnectionState.Connected("$SOCKS_HOST:$SOCKS_PORT"))
        updateNotification(getString(R.string.state_connected))
        DiagnosticsLog.i(TAG, "All checks passed — tunnel is ready.")

        superviseEngine(resolved)
    }

    /**
     * SMART AUTO (root-cause rework of the broken Auto protocol): fingerprint
     * the network's DPI first (see [SmartAuto]), then walk an ordered ladder
     * of concrete strategies — protocol + obfuscation + the IP ranges that
     * actually answered on THIS network — until one passes the full 4-step
     * self-test. Returns the strategy that won so the supervisor restarts the
     * engine with the SAME working configuration.
     */
    private suspend fun connectSmartAuto(userProfile: ConnectionProfile): ConnectionProfile {
        SrvtherController.setState(ConnectionState.Launching)
        updateNotification(getString(R.string.state_analyzing))
        val fingerprint = SmartAuto.fingerprint(this)
        val plan = SmartAuto.buildPlan(userProfile, fingerprint)
        return runLadder(plan, getString(R.string.err_auto_failed))
    }

    /**
     * Two-pass plan for a protocol the user picked by hand (MASQUE, WireGuard
     * or Gool).
     *
     * 1.2.2 "MASQUE hangs forever" FIX: a hand-picked protocol used to get ONE
     * attempt with the full scan budget of the selected scan mode — up to 150 s
     * on Balanced and 300 s on Thorough — with no second chance. On a network
     * where QUIC/UDP is throttled that means the user stares at "Connecting"
     * for minutes and then just fails, while Smart mode (which walks a ladder
     * of shorter, hardened attempts) connects in seconds. So the chosen
     * protocol now gets:
     *   1. a first pass exactly as configured, on a capped budget, and
     *   2. if that fails, the SAME protocol again with anti-DPI hardening
     *      (obfuscation on, plus HTTP/2 + TLS fragmentation + ECH for MASQUE)
     *      on the full budget.
     * The protocol the user chose is never swapped for another one.
     */
    private fun directPlan(profile: ConnectionProfile): List<AutoCandidate> {
        val fullBudget = profile.connectTimeoutMs()
        val hardenedNoize = if (profile.noize == Noize.OFF) Noize.FIREWALL else profile.noize
        val masque = profile.protocol == Protocol.MASQUE
        val hardened = profile.copy(
            noize = hardenedNoize,
            masqueHttp2 = profile.masqueHttp2 || masque,
            fragment = profile.fragment || masque,
            ech = profile.ech || masque,
        )
        if (hardened == profile) {
            return listOf(
                AutoCandidate(profile, fullBudget, "${profile.protocol.name} · as configured"),
            )
        }
        return listOf(
            AutoCandidate(
                profile,
                fullBudget.coerceAtMost(FIRST_PASS_MAX_MS),
                "${profile.protocol.name} · as configured",
            ),
            AutoCandidate(
                hardened,
                fullBudget,
                "${profile.protocol.name} · noize=${hardenedNoize.name.lowercase()}" +
                    (if (masque) " · h2 · fragment · ech" else "") + " (anti-DPI pass)",
            ),
        )
    }

    /**
     * Walks a ladder of strategies until one comes up and passes the full
     * self-test. Each failed rung is torn down before the next is tried.
     */
    private suspend fun runLadder(
        plan: List<AutoCandidate>,
        failureMessage: String,
    ): ConnectionProfile {
        var lastError: Exception? = null

        plan.forEachIndexed { index, candidate ->
            DiagnosticsLog.i(TAG, "Attempt ${index + 1}/${plan.size} → ${candidate.label}")
            try {
                connectAttempt(candidate.profile, candidate.timeoutMs)
                DiagnosticsLog.i(TAG, "Connected using ${candidate.label}")
                return candidate.profile
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                DiagnosticsLog.w(
                    TAG,
                    "${candidate.label} failed (${e.message}) — moving to the next strategy.",
                )
                cleanupNativeOnly()
                Diagnostics.resetChecks()
            }
        }

        throw IllegalStateException(failureMessage, lastError)
    }

    /**
     * One full connect attempt with a CONCRETE protocol: launch engine, wait
     * for SOCKS5, bring up TUN/proxy, and gate on the 4-step self-test.
     * Throws on any failure; the caller decides whether to retry differently.
     */
    private suspend fun connectAttempt(
        profile: ConnectionProfile,
        timeoutMs: Long,
    ) {
        SrvtherController.setState(ConnectionState.Launching)
        updateNotification(getString(R.string.state_launching))
        // 1.2.2 PROTOCOL-SWITCH FIX: never start an engine on top of a dying
        // one. Tear the previous natives down and wait for the local SOCKS5
        // port to be released first, otherwise the probe below can "see" the
        // old listener and the whole attempt is verified against a socket that
        // is about to disappear.
        // Leaving lockdown (if any): the blackhole TUN is torn down here.
        lockdownTunActive = false
        cleanupNativeOnly()
        if (!PortProbe.awaitClosed(SOCKS_HOST, SOCKS_PORT, PORT_RELEASE_WAIT_MS)) {
            DiagnosticsLog.w(
                TAG,
                "Local port $SOCKS_PORT is still busy after ${PORT_RELEASE_WAIT_MS / 1000}s — starting anyway.",
            )
        }
        if (!PortProbe.awaitClosed(SOCKS_HOST, 10808, PORT_RELEASE_WAIT_MS)) {
            DiagnosticsLog.w(
                TAG,
                "Local port ${10808} is still busy after ${PORT_RELEASE_WAIT_MS / 1000}s — starting anyway.",
            )
        }
        DiagnosticsLog.i(TAG, "Launching engine (libsrvther.so)…")
        engine = SrvtherProcess(applicationInfo.nativeLibraryDir, filesDir).also { it.start(profile) }

        SrvtherController.setState(ConnectionState.Connecting)
        updateNotification(getString(R.string.state_connecting))
        // Timeout comes from the caller: the profile's scan-mode budget for a
        // direct connect, or the per-candidate budget in the Smart Auto ladder.
        DiagnosticsLog.i(
            TAG,
            "Waiting for SOCKS5 on $SOCKS_HOST:$SOCKS_PORT… (scan=${profile.scanMode}, timeout=${timeoutMs / 1000}s)",
        )
        val opened = PortProbe.awaitOpen(SOCKS_HOST, SOCKS_PORT, timeoutMs) { engine?.isAlive() == true }
        if (!opened) {
            val engineDied = engine?.isAlive() != true
            if (engineDied) {
                DiagnosticsLog.e(TAG, "Engine exited before it opened the SOCKS5 port.")
                throw IllegalStateException(getString(R.string.err_engine_died))
            }
            DiagnosticsLog.e(TAG, "Engine still scanning after ${timeoutMs / 1000}s — SOCKS5 port never opened.")
            throw IllegalStateException(getString(R.string.err_engine_timeout))
        }
        DiagnosticsLog.i(TAG, "SOCKS5 port is up.")

        val isChainedVless = profile.vlessConfig.isNotBlank()
        val targetPort = if (isChainedVless) 10808 else SOCKS_PORT

        if (profile.vlessConfig.isNotBlank()) {
            DiagnosticsLog.i(TAG, "Starting Xray-core for VLESS...")
            try {
                app.srvther.core.CoreNativeManager.initCoreEnv(this)
                val xrayJson = app.srvther.core.XrayConfigGenerator.generate(profile.vlessConfig)
                val handler = object : libv2ray.CoreCallbackHandler {
                    override fun onStart() { DiagnosticsLog.i(TAG, "Xray-core started") }
                    override fun onStop() { DiagnosticsLog.i(TAG, "Xray-core stopped") }
                }
                xrayController = app.srvther.core.CoreNativeManager.newCoreController(handler)
                val startErr = xrayController?.startLoop(xrayJson)
                if (startErr != null && startErr.isNotEmpty()) {
                    DiagnosticsLog.e(TAG, "Xray-core failed: ")
                    throw IllegalStateException("Xray-core failed to start")
                }
                
                val psiOpened = app.srvther.core.PortProbe.awaitOpen("127.0.0.1", 10808, 10000)
                if (!psiOpened) {
                    throw IllegalStateException("Xray-core SOCKS5 port timeout")
                }
                DiagnosticsLog.i(TAG, "Xray SOCKS5 port 10808 is up.")
            } catch (e: Exception) {
                DiagnosticsLog.e(TAG, "Xray error: ")
                throw IllegalStateException("Failed to start Xray")
            }
        }

        if (profile.proxyMode) {
            // Proxy mode: DON'T capture the whole device through a system TUN.
            // Instead expose the engine's SOCKS5 + an HTTP proxy so individual
            // apps (or the Wi-Fi proxy setting) can opt in. Route through the
            // correct port (Psiphon 1820 when chained, Srvther 1819 when direct).
            val shareReady = ShareBridge.startSync(localOnly = !profile.lanShare, upstreamPort = targetPort)
            if (!shareReady) {
                DiagnosticsLog.e(TAG, "Proxy mode: the fixed local proxy ports could not be opened (see errors above).")
                throw IllegalStateException(getString(R.string.err_proxy_ports))
            }
            DiagnosticsLog.i(
                TAG,
                "Proxy mode: system TUN skipped. Local proxy ready — " +
                    "SOCKS5 127.0.0.1:${ShareBridge.SOCKS_SHARE_PORT}, HTTP 127.0.0.1:${ShareBridge.HTTP_SHARE_PORT}",
            )
        } else {
            establishTun(profile)
            startTun2Socks(profile, targetPort, isChainedVless)
            // LAN sharing: if the user enabled it, expose the tunnel to other
            // devices on the same Wi-Fi/hotspot (HTTP + SOCKS5 bridge).
            if (profile.lanShare) ShareBridge.start(localOnly = false)
        }

        SrvtherController.setState(ConnectionState.Verifying)
        updateNotification(getString(R.string.state_verifying))
        DiagnosticsLog.i(
            TAG,
            if (profile.proxyMode) "Proxy started. Verifying end-to-end connectivity…"
            else "TUN + hev tunnel started. Verifying end-to-end connectivity…",
        )

        val diagPort =
            if (profile.proxyMode) ShareBridge.socksPort.value ?: targetPort
            else targetPort
        val healthy = runCatching { Diagnostics.run(port = diagPort) }.getOrDefault(false)
        if (!healthy) {
            DiagnosticsLog.e(TAG, "Self-test failed — refusing to report Connected.")
            throw IllegalStateException(getString(R.string.err_selftest))
        }

        // Informational only: report where the tunnel actually came out.
        // WARP edges are anycast, so the exit location is decided by the
        // engine's endpoint selection and the operator's routing, not by the
        // app. Nothing here can reject or override that choice.
        val exit = SrvtherController.ipInfo.value?.takeIf { it.viaTunnel }
        if (exit != null) {
            DiagnosticsLog.i(
                TAG,
                "Exit verified through the tunnel: ${exit.ip} (${exit.countryCode ?: "??"})",
            )
        }
    }

    /** Keeps the engine alive; retries with backoff if it dies. */
    private suspend fun superviseEngine(profile: ConnectionProfile) {
        var attempt = 0
        val isChainedVless = profile.vlessConfig.isNotBlank()
        while (currentScopeActive()) {
            val srvtherAlive = engine?.isAlive() == true
            val xrayAlive = true
            if (srvtherAlive && xrayAlive) {
                attempt = 0
                engine?.awaitExit(WATCHDOG_INTERVAL_MS)
                // After waking, check both engines are still alive + probe
                // through the ACTUAL exit port the TUN is using.
                val stillSrvtherAlive = engine?.isAlive() == true
                val stillPsiAlive = true
                if (stillSrvtherAlive && stillPsiAlive) {
                    if (probeTunnelCycle(if (isChainedVless) 10808 else SOCKS_PORT)) {
                        probeFailures = 0
                    } else if (++probeFailures >= WATCHDOG_FAIL_CYCLES) {
                        DiagnosticsLog.w(
                            TAG,
                            "Watchdog: tunnel dead across $WATCHDOG_FAIL_CYCLES consecutive checks -- restarting.",
                        )
                        probeFailures = 0
                        try { xrayController?.stopLoop() } catch(e: Exception) {}
                        xrayController = null
                        engine?.stop()
                    }
                }
                continue
            }

            // At least one engine died — restart both.
            if (attempt >= maxRetries(profile)) {
                if (profile.killSwitch || profile.strictKillSwitch) {
                    enterLockdown(profile)
                    return
                }
                throw IllegalStateException(getString(R.string.err_engine_died))
            }
            val backoff = BACKOFF[attempt.coerceAtMost(BACKOFF.size - 1)]
            attempt++
            SrvtherController.setState(ConnectionState.Reconnecting(attempt, maxRetries(profile)))
            updateNotification(getString(R.string.state_reconnecting))
            delay(backoff)

            // Stop any surviving half
            try { xrayController?.stopLoop() } catch(e: Exception) {}
            xrayController = null
            engine?.stop()
            engine = null

            // Restart Srvther
            engine = SrvtherProcess(applicationInfo.nativeLibraryDir, filesDir).also { it.start(profile) }
            if (!PortProbe.awaitOpen(SOCKS_HOST, SOCKS_PORT, profile.connectTimeoutMs()) { engine?.isAlive() == true }) {
                continue
            }

            // Restart Xray if chained
            if (isChainedVless) {
                app.srvther.core.CoreNativeManager.initCoreEnv(this)
                val xrayJson = app.srvther.core.XrayConfigGenerator.generate(profile.vlessConfig)
                val handler = object : libv2ray.CoreCallbackHandler {
                    override fun onStart() {}
                    override fun onStop() {}
                }
                xrayController = app.srvther.core.CoreNativeManager.newCoreController(handler)
                xrayController?.startLoop(xrayJson)
                if (!app.srvther.core.PortProbe.awaitOpen("127.0.0.1", 10808, 10000)) {
                    DiagnosticsLog.w(TAG, "Xray failed to restart - retrying.")
                    continue
                }
            }


            // Verify end-to-end
            val diagPort = if (isChainedVless) 10808 else SOCKS_PORT
            SrvtherController.setState(ConnectionState.Verifying)
            updateNotification(getString(R.string.state_verifying))
            if (runCatching { Diagnostics.run(port = diagPort) }.getOrDefault(false)) {
                attempt = 0
                SrvtherController.setState(ConnectionState.Connected("$SOCKS_HOST:$diagPort"))
                updateNotification(getString(R.string.state_connected))
            } else {
                DiagnosticsLog.w(TAG, "Self-test failed after restart — retrying.")
                try { xrayController?.stopLoop() } catch(e: Exception) {}
                xrayController = null
                engine?.stop()
            }
        }
    }

    private fun currentScopeActive(): Boolean = runJob?.isActive ?: false

    private fun establishTun(profile: ConnectionProfile) {
        // User-tunable MTU (defaults to 1280 — safe for Iranian mobile/DPI).
        // Clamped to a sane range so a bad saved value can't break establish().
        val mtu = profile.mtu.coerceIn(576, 9000)
        val builder = Builder()
            .setSession("Srvther")
            .setMtu(mtu)
            // The TUN address MUST match hev's tunnel.ipv4/ipv6 (see writeHevConfig).
            .addAddress(TunnelConfig.TUN_IPV4, TunnelConfig.TUN_IPV4_PREFIX)
            .addAddress(TunnelConfig.TUN_IPV6, TunnelConfig.TUN_IPV6_PREFIX)
            .addRoute("0.0.0.0", 0)

        // IPv6 LEAK PROTECTION (1.2.4): on by default -- the v6 default
        // route keeps IPv6 traffic inside the tunnel. Can be disabled for
        // networks where a default v6 route breaks connectivity.
        if (profile.ipv6LeakProtection) {
            builder.addRoute("::", 0)
        }

        // KILL SWITCH (1.2.4): a blocking interface never falls back to
        // direct traffic while the tunnel is not forwarding.
        if (profile.killSwitch || profile.strictKillSwitch) {
            builder.setBlocking(true)
        }

        val isChainedVless = profile.vlessConfig.isNotBlank()
        if (isChainedVless) {
            builder.addDnsServer("198.18.0.2")
        } else {
            TunnelConfig.DNS_SERVERS.forEach { builder.addDnsServer(it) }
        }

        // Split tunneling + loop prevention (keeps the engine's own traffic off
        // the TUN, equivalent to v2rayNG's in-process protect()).
        applyAppFilter(builder, profile)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        tun = builder.establish()
            ?: throw IllegalStateException("Failed to establish the VPN interface")
        DiagnosticsLog.i(
            TAG,
            "TUN established: ipv4=${TunnelConfig.TUN_IPV4}/${TunnelConfig.TUN_IPV4_PREFIX} " +
                "ipv6=${TunnelConfig.TUN_IPV6}/${TunnelConfig.TUN_IPV6_PREFIX} mtu=$mtu " +
                "split=${profile.splitMode} apps=${profile.splitApps.size} dns=${TunnelConfig.DNS_SERVERS}",
        )
    }

    /**
     * Applies the split-tunnel policy and always keeps the app's own engine
     * traffic off the TUN (loop prevention).
     *
     * - OFF     : everything routes through the VPN except our own package.
     * - INCLUDE : ONLY the chosen apps route through the VPN. Our own package is
     *             implicitly excluded because it is never added to the allow-list.
     * - EXCLUDE : everything routes through the VPN except the chosen apps + us.
     */
    private fun applyAppFilter(builder: Builder, profile: ConnectionProfile) {
        val apps = profile.splitApps.filter { it.isNotBlank() && it != packageName }
        when (profile.splitMode) {
            SplitMode.INCLUDE -> {
                if (apps.isEmpty()) {
                    // Nothing selected -> fall back to OFF so we don't build a
                    // tunnel that carries no traffic at all.
                    safeDisallow(builder, packageName)
                    return
                }
                apps.forEach { safeAllow(builder, it) }
            }
            SplitMode.EXCLUDE -> {
                safeDisallow(builder, packageName)
                // Blocked apps must stay INSIDE the TUN so the filter bridge
                // can drop their traffic; excluding them would give them
                // direct internet instead of none.
                apps.filter { it !in profile.blockedApps }.forEach { safeDisallow(builder, it) }
            }
            SplitMode.OFF -> safeDisallow(builder, packageName)
        }
    }

    private fun safeAllow(builder: Builder, pkg: String) {
        try {
            builder.addAllowedApplication(pkg)
        } catch (_: Exception) {
            DiagnosticsLog.w(TAG, "addAllowedApplication failed for $pkg (not installed?)")
        }
    }

    private fun safeDisallow(builder: Builder, pkg: String) {
        try {
            builder.addDisallowedApplication(pkg)
        } catch (_: Exception) {
            if (pkg != packageName) DiagnosticsLog.w(TAG, "addDisallowedApplication failed for $pkg")
        }
    }

    private fun startTun2Socks(profile: ConnectionProfile, targetPort: Int = SOCKS_PORT, isVless: Boolean = false) {
        if (profile.blockedApps.isNotEmpty() && !isVless) {
            val pfd = tun ?: throw IllegalStateException("TUN descriptor is null")
            val bridge = SocksTunBridge(
                vpnService = this,
                tunDescriptor = pfd,
                socksHost = SOCKS_HOST,
                socksPort = targetPort,
                mtu = profile.mtu.coerceIn(576, 9000),
                blockedPackagesProvider = { profile.blockedApps.toSet() },
                routingEngine = RoutingEngine(emptyList()),
            )
            DiagnosticsLog.i(TAG, "Starting userspace filter bridge (blocked apps=${profile.blockedApps.size}, targetPort=$targetPort)")
            bridge.start()
            tunBridge = bridge
            return
        }
        val config = writeHevConfig(profile.mtu.coerceIn(576, 9000), targetPort, isVless)
        val fd = tun?.fd ?: throw IllegalStateException("TUN descriptor is null")
        DiagnosticsLog.i(TAG, "Starting hev-socks5-tunnel in-process (fd=$fd, targetPort=$targetPort)")
        HevTunnel.start(config.absolutePath, fd)
        tunnelStarted = true
    }

    /**
     * Writes the hev-socks5-tunnel config.
     */
    private fun writeHevConfig(mtu: Int, targetPort: Int = SOCKS_PORT, isVless: Boolean = false): File {
        val file = File(filesDir, "hev.yaml")
        // Keep udp mode as 'udp' so that non-DNS UDP traffic is sent as UDP ASSOCIATE.
        // Psiphon will instantly reject UDP ASSOCIATE, triggering fast fallback to TCP
        // in apps (e.g. QUIC falls back to TCP). If we set it to 'off' or 'tcp', it wraps
        // UDP in TCP, causing connections to hang indefinitely.
        
        val yaml = buildString {
            appendLine("tunnel:")
            appendLine("  mtu: $mtu")
            appendLine("  ipv4: ${TunnelConfig.TUN_IPV4}")
            appendLine("  ipv6: '${TunnelConfig.TUN_IPV6}'")
            appendLine("socks5:")
            appendLine("  address: $SOCKS_HOST")
            appendLine("  port: $targetPort")
            appendLine("  udp: 'udp'")
            
            if (isVless) {
                appendLine("mapdns:")
                appendLine("  address: 198.18.0.2")
                appendLine("  port: 53")
                appendLine("  network: 100.64.0.0")
                appendLine("  netmask: 255.192.0.0")
                appendLine("  cache-size: 10000")
            }
            
            appendLine("misc:")
            appendLine("  task-stack-size: 86016")
            appendLine("  connect-timeout: 5000")
            appendLine("  tcp-read-write-timeout: 300000")
            appendLine("  udp-read-write-timeout: 120000")
            appendLine("  log-level: warn")
        }
        file.writeText(yaml)
        DiagnosticsLog.i(TAG, "hev.yaml written:\n$yaml")
        return file
    }

    private fun stopEverything() {
        SrvtherController.setState(ConnectionState.Disconnecting)
        updateNotification(getString(R.string.state_disconnecting))
        val job = runJob
        runJob = null
        // DISCONNECT MUST BE INSTANT. Order matters:
        //   1. cancel the session coroutine (does not wait for it),
        //   2. kill the natives right away — this is what actually makes the
        //      tunnel stop, and it also unblocks any wait the session
        //      coroutine is parked in,
        //   3. flip the UI to Idle and drop the foreground notification,
        //   4. only THEN join the finished coroutine, off the critical path.
        // The previous order (join → cleanup) made the button sit on
        // "Disconnecting…" for as long as the supervisor's engine wait had
        // left to run — up to a full minute.
        job?.cancel()
        stopJob = scope.launch(Dispatchers.IO) {
            cleanupNativeOnly()
            // STALE-CIRCLES FIX (part 2): clear the finished session's results
            // right at disconnect, so the panel never carries green circles
            // from a dead session into the next connect.
            Diagnostics.resetChecks()
            EngineMeta.reset()
            SrvtherController.setState(ConnectionState.Idle)
            SrvtherTileService.requestUpdate(this@SrvtherVpnService)
            stopForegroundCompat()
            stopSelf()
            job?.join()
        }
    }

    /** Max automatic engine restarts (Smart Reconnect, 1.2.4). */
    private fun maxRetries(profile: ConnectionProfile): Int =
        if (profile.smartReconnect) profile.reconnectRetryLimit.coerceIn(1, 50) else 50

    /**
     * WATCHDOG PROBE, hardened (1.2.4 periodic-outage root-cause fix).
     *
     * The old probe was a single TCP connect to 1.1.1.1:53 with a 5 s
     * timeout. On high-RTT, lossy links (the tunnel's own baseline RTT is
     * 350-550 ms and DPI throttling causes multi-second UDP stalls that heal
     * by themselves) that lone probe fails SPURIOUSLY -- two unlucky probes
     * 30 s apart were enough to kill a perfectly healthy engine and force a
     * full endpoint rescan, which is itself a 30-90 s total outage. The cure
     * had become the disease: the periodic "no site opens, then it works
     * again" the user saw every few minutes was the watchdog restarting a
     * tunnel that was only briefly stalled.
     *
     * A check now only counts as failed when THREE attempts in a row --
     * spread over three different anycast resolvers, 8 s timeout each, 1.5 s
     * apart -- all fail, and the engine is restarted only after THREE
     * consecutive failed checks (90 s+ of continuously proven dead tunnel).
     * Brief self-healing stalls no longer trigger restarts, a genuinely dead
     * session still recovers automatically, and MASQUE's in-engine reconnect
     * loop gets room to finish before the app steps in.
     */
    private suspend fun probeTunnelCycle(probePort: Int = SOCKS_PORT): Boolean {
        repeat(PROBE_ATTEMPTS) { attempt ->
            if (probeTunnelOnce(PROBE_TARGETS[attempt % PROBE_TARGETS.size], probePort)) return true
            if (attempt < PROBE_ATTEMPTS - 1) delay(PROBE_RETRY_GAP_MS)
        }
        return false
    }

    /** Single TCP connect to [target] ("host:port") THROUGH the given local SOCKS5 listener. */
    private fun probeTunnelOnce(target: String, probePort: Int = SOCKS_PORT): Boolean = runCatching {
        val proxy = java.net.Proxy(
            java.net.Proxy.Type.SOCKS,
            java.net.InetSocketAddress(SOCKS_HOST, probePort),
        )
        java.net.Socket(proxy).use {
            it.connect(
                java.net.InetSocketAddress(target.substringBefore(':'), target.substringAfter(':').toInt()),
                PROBE_TIMEOUT_MS,
            )
        }
        true
    }.getOrDefault(false)

    /**
     * KILL SWITCH lockdown (1.2.4): stop the engine and the forwarder but
     * KEEP a blocking full-tunnel TUN up, so every packet is blackholed
     * instead of leaking direct. The service stays foreground; connecting
     * again or disconnecting lifts the lockdown.
     */
    private fun enterLockdown(profile: ConnectionProfile) {
        val job = runJob
        runJob = null
        job?.cancel()
        stopJob = scope.launch(Dispatchers.IO) {
            cleanupForwardingOnly()
            ensureLockdownTun(profile)
            lockdownTunActive = true
            Diagnostics.resetChecks()
            EngineMeta.reset()
            SrvtherController.setState(ConnectionState.Error(getString(R.string.state_killswitch)))
            updateNotification(getString(R.string.state_killswitch))
            SrvtherTileService.requestUpdate(this@SrvtherVpnService)
            job?.join()
        }
    }

    /** Stops sharing, the forwarder and both engines but deliberately KEEPS [tun]. */
    private fun cleanupForwardingOnly() {
        try {
            ShareBridge.stop()
        } catch (_: Throwable) {
        }
        tunBridge?.let { runCatching { it.stop() } }
        tunBridge = null
        if (tunnelStarted) {
            try {
                HevTunnel.stop()
            } catch (_: Throwable) {
            }
            tunnelStarted = false
        }
        try {
            try { xrayController?.stopLoop() } catch(e: Exception) {}
        } catch (_: Throwable) {
        }
        xrayController = null
        try {
            engine?.stop()
        } catch (_: Throwable) {
        }
        engine = null
    }

    /** (Re)builds the TUN as a full-tunnel blackhole: routes everything, reads nothing. */
    private fun ensureLockdownTun(profile: ConnectionProfile) {
        runCatching { tun?.close() }
        tun = null
        val builder = Builder()
            .setSession("Srvther KillSwitch")
            .setMtu(profile.mtu.coerceIn(576, 9000))
            .addAddress(TunnelConfig.TUN_IPV4, TunnelConfig.TUN_IPV4_PREFIX)
            .addRoute("0.0.0.0", 0)
            .setBlocking(true)
        if (profile.ipv6LeakProtection) {
            builder.addAddress(TunnelConfig.TUN_IPV6, TunnelConfig.TUN_IPV6_PREFIX)
            builder.addRoute("::", 0)
        }
        tun = runCatching { builder.establish() }.getOrNull()
    }

    private fun cleanupNativeOnly() {
        // Stop sharing first: without the tunnel the bridge would leak direct.
        try {
            ShareBridge.stop()
        } catch (_: Throwable) {
        }
        tunBridge?.let { runCatching { it.stop() } }
        tunBridge = null
        if (tunnelStarted) {
            try {
                HevTunnel.stop()
            } catch (_: Throwable) {
            }
            tunnelStarted = false
        }
        try {
            try { xrayController?.stopLoop() } catch(e: Exception) {}
        } catch (_: Throwable) {
        }
        xrayController = null
        try {
            engine?.stop()
        } catch (_: Throwable) {
        }
        engine = null
        try {
            tun?.close()
        } catch (_: Throwable) {
        }
        tun = null
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onRevoke() {
        stopEverything()
        super.onRevoke()
    }

    override fun onDestroy() {
        runJob?.cancel()
        cleanupNativeOnly()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SrvtherVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, SrvtherApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.state_disconnecting), disconnectIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
        // Keep the Quick Settings tile in sync with every state transition.
        SrvtherTileService.requestUpdate(this)
        // Keep the home-screen widget (feature merge) in sync too.
        // Cheap: returns immediately when no widget is placed.
        SrvtherWidgetProvider.updateAllWidgets(this)
    }

    companion object {
        const val ACTION_CONNECT = "app.srvther.CONNECT"
        const val ACTION_DISCONNECT = "app.srvther.DISCONNECT"
        const val EXTRA_PROFILE = "profile"

        private const val NOTIF_ID = 0x4145
        private const val TAG = "vpn"
        private const val SOCKS_HOST = TunnelConfig.SOCKS_HOST
        private const val SOCKS_PORT = TunnelConfig.SOCKS_PORT
        private const val MTU = TunnelConfig.MTU
        private const val MAX_RETRIES = 3
        private val BACKOFF = longArrayOf(2000L, 5000L, 10000L)

        /**
         * Upper bound for one blocking wait on the engine process (1.2.2).
         * The supervisor no longer polls; it parks on the process itself and
         * only wakes up this often to re-check its own cancellation state.
         */
        private const val SUPERVISOR_WAIT_MS = 60_000L

        /** Watchdog probe cadence while the tunnel is up (1.2.4). */
        private const val WATCHDOG_INTERVAL_MS = 30_000L

        /**
         * Consecutive failed checks before the engine is restarted (1.2.4
         * hardening): three failed checks = 90 s+ of proven dead tunnel, so
         * only a genuinely dead session is restarted.
         */
        private const val WATCHDOG_FAIL_CYCLES = 3

        /**
         * Attempts per watchdog check, rotating over anycast resolvers so one
         * blocked or slow target can never fake a dead tunnel (1.2.4 fix).
         */
        private const val PROBE_ATTEMPTS = 3
        private val PROBE_TARGETS = arrayOf("1.1.1.1:53", "1.0.0.1:53", "9.9.9.9:53")
        private const val PROBE_TIMEOUT_MS = 8_000
        private const val PROBE_RETRY_GAP_MS = 1_500L

        /**
         * How long to wait for the previous engine to release the local SOCKS5
         * port before starting a new one (1.2.2 protocol-switch fix).
         */
        private const val PORT_RELEASE_WAIT_MS = 3_000L

        /**
         * Cap for the FIRST attempt of a hand-picked protocol, so a throttled
         * network cannot hold the user on "Connecting" for the whole scan
         * budget before the hardened second pass is even tried.
         */
        private const val FIRST_PASS_MAX_MS = 75_000L
    }
}
