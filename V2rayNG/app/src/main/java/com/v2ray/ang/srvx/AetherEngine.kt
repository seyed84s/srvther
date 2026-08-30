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
    @Volatile
    private var isStoppingManually = false
    const val SOCKS_PORT = 1819

    private fun getExecutableBinary(context: Context): File? {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val binInNative = File(nativeLibDir, "libaether.so")
        if (binInNative.exists()) {
            binInNative.setExecutable(true, false)
            if (binInNative.canExecute()) return binInNative
        }

        // Fallback 1: Files directory
        val binInFiles = File(context.filesDir, "libaether.so")
        if (binInFiles.exists() && binInFiles.length() > 1000000) {
            binInFiles.setExecutable(true, false)
            if (binInFiles.canExecute()) return binInFiles
        }

        // Fallback 2: Copy from nativeLibDir if present
        if (binInNative.exists()) {
            try {
                binInNative.copyTo(binInFiles, overwrite = true)
                binInFiles.setExecutable(true, false)
                if (binInFiles.exists()) return binInFiles
            } catch (e: Exception) {
                Log.e("AetherEngine", "Failed copying from nativeLibDir", e)
            }
        }

        // Fallback 3: Extract from APK
        try {
            val apkFile = File(context.applicationInfo.sourceDir)
            if (apkFile.exists()) {
                val zip = java.util.zip.ZipFile(apkFile)
                val abis = android.os.Build.SUPPORTED_ABIS
                var entry: java.util.zip.ZipEntry? = null
                for (abi in abis) {
                    entry = zip.getEntry("lib/$abi/libaether.so")
                    if (entry != null) break
                }
                if (entry != null) {
                    zip.getInputStream(entry).use { input ->
                        binInFiles.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    binInFiles.setExecutable(true, false)
                    Log.i("AetherEngine", "Extracted libaether.so from APK to ${binInFiles.absolutePath}")
                    return binInFiles
                }
            }
        } catch (e: Exception) {
            Log.e("AetherEngine", "Failed extracting from APK", e)
        }

        return if (binInNative.exists()) binInNative else if (binInFiles.exists()) binInFiles else null
    }

    fun start(context: Context, mode: String) {
        stop()
        isStoppingManually = false

        val bin = getExecutableBinary(context)
        if (bin == null || !bin.exists()) {
            Log.e("AetherEngine", "Engine binary missing")
            return
        }

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
                    if (!isStoppingManually) {
                        Log.w("AetherEngine", "Engine exited unexpectedly! Tearing down VPN...")
                        com.v2ray.ang.util.MessageUtil.sendMsg2Service(context, com.v2ray.ang.AppConfig.MSG_STATE_STOP, "")
                    }
                }
            }, "AetherEngine-Output").apply { isDaemon = true }.start()

        } catch (e: Exception) {
            Log.e("AetherEngine", "Failed to start engine", e)
        }
    }

    fun stop() {
        isStoppingManually = true
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
