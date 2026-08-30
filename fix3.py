import os
import re

f = 'app/src/main/java/app/srvther/vpn/AetherVpnService.kt'
with open(f, 'r', encoding='utf-8') as file:
    c = file.read()

# Remove psiphon start
c = re.sub(
    r'if \(isChainedPsiphon\) \{.*?DiagnosticsLog\.i\(TAG, \"Psiphon SOCKS5 port \$\{TunnelConfig\.PSIPHON_SOCKS_PORT\} is up\.\"\)\n\s*\}',
    '''if (profile.vlessConfig.isNotBlank()) {
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
                    DiagnosticsLog.e(TAG, "Xray-core failed: \")
                    throw IllegalStateException("Xray-core failed to start")
                }
                
                val psiOpened = studio.cluvex.aether.core.PortProbe.awaitOpen("127.0.0.1", 10808, 10000)
                if (!psiOpened) {
                    throw IllegalStateException("Xray-core SOCKS5 port timeout")
                }
                DiagnosticsLog.i(TAG, "Xray SOCKS5 port 10808 is up.")
            } catch (e: Exception) {
                DiagnosticsLog.e(TAG, "Xray error: \")
                throw IllegalStateException("Failed to start Xray")
            }
        }''',
    c, flags=re.DOTALL
)

c = c.replace('private var psiphonEngine: PsiphonProcess? = null', 'private var xrayController: libv2ray.CoreController? = null')
c = c.replace('psiphonEngine?.stop()', 'try { xrayController?.stopLoop() } catch(e: Exception) {}')
c = c.replace('psiphonEngine = null', 'xrayController = null')
c = c.replace('val isChainedPsiphon = profile.psiphonEnabled && profile.psiphonRegion != PsiphonRegion.DIRECT', 'val isChainedPsiphon = profile.vlessConfig.isNotBlank()')
c = c.replace('import app.srvther.core.PsiphonProcess', '')
c = c.replace('import app.srvther.model.PsiphonRegion', '')
c = c.replace('studio.cluvex.aether.core.PortProbe', 'app.srvther.core.PortProbe')

with open(f, 'w', encoding='utf-8') as file:
    file.write(c)
