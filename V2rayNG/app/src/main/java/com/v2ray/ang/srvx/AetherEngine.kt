package com.v2ray.ang.srvx

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs the native Aether engine (Rust) as a child process.
 * The engine exposes a SOCKS5 proxy on 127.0.0.1:1819.
 */
object AetherEngine {
    private var process: Process? = null
    const val SOCKS_PORT = 1819

    fun start(context: Context, mode: String) {
        stop()

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val bin = File(nativeLibDir, "libaether.so")
        if (!bin.exists()) {
            Log.e("AetherEngine", "Engine binary missing: ${bin.absolutePath}")
            return
        }

        // Make executable if needed (usually done by Android PM, but just in case)
        bin.setExecutable(true)

        // Base arguments
        val command = mutableListOf(bin.absolutePath)
        
        when (mode) {
            "masque" -> command.add("--masque")
            "wg" -> command.add("--wg")
            "gool" -> command.add("--gool")
            else -> command.add("--wg") // Default fallback
        }

        // Apply scan mode based on preferences
        when (AetherConfigManager.getScanMode()) {
            "balanced" -> command.add("--balanced")
            "thorough" -> command.add("--thorough")
            else -> command.add("--turbo") // turbo is default
        }

        val builder = ProcessBuilder(command)
            .directory(context.cacheDir)
            .redirectErrorStream(true)
        
        builder.environment().apply {
            put("HOME", context.cacheDir.absolutePath)
            put("TMPDIR", context.cacheDir.absolutePath)
            put("AETHER_LOG_LEVEL", "off") // Fix overheating: disable spammy debug logs
            
            // Zero Trust credentials if present
            val team = AetherConfigManager.getZeroTrustTeam()
            val token = AetherConfigManager.getZeroTrustToken()
            if (team.isNotBlank() && token.isNotBlank()) {
                command.add("--team")
                command.add(team)
                put("AETHER_ACCESS_TOKEN", token)
            }
        }

        try {
            val proc = builder.start()
            process = proc
            Log.i("AetherEngine", "Spawned Aether Engine with args: $command")

            // Drain stdout so the buffer doesn't fill up and block the engine
            Thread({
                try {
                    proc.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            Log.d("AetherEngine", line)
                        }
                    }
                    proc.waitFor()
                } catch (e: Exception) {
                    Log.e("AetherEngine", "Error reading engine output", e)
                } finally {
                    // Engine crashed or stopped. Tear down the VPN to save battery/radio.
                    Log.w("AetherEngine", "Engine exited! Tearing down VPN...")
                    com.v2ray.ang.util.MessageUtil.sendMsg2Service(context, com.v2ray.ang.AppConfig.MSG_STATE_STOP, "")
                }
            }, "AetherEngine-Output").apply { isDaemon = true }.start()

        } catch (e: Exception) {
            Log.e("AetherEngine", "Failed to start engine", e)
        }
    }

    fun stop() {
        process?.let {
            Log.i("AetherEngine", "Stopping Aether Engine...")
            it.destroy()
            try {
                if (!it.waitFor(2, TimeUnit.SECONDS)) {
                    // Timeout
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        process = null
    }
}
