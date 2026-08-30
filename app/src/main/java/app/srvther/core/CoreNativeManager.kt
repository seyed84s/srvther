package app.srvther.core

import android.content.Context
import android.util.Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicBoolean

/**
 * V2Ray Native Library Manager
 *
 * Thread-safe singleton wrapper for Libv2ray native methods.
 * Provides initialization protection and unified API for V2Ray core operations.
 */
object CoreNativeManager {
    private val initialized = AtomicBoolean(false)

    /**
     * Initialize V2Ray core environment.
     * This method is thread-safe and ensures initialization happens only once.
     * Subsequent calls will be ignored silently.
     *
     */
    fun initCoreEnv(context: Context?) {
        if (initialized.compareAndSet(false, true)) {
            try {
                // Seq.setContext(context?.applicationContext)
                val assetPath = context?.filesDir?.absolutePath ?: ""
                val deviceId = try {
                    val androidId = android.provider.Settings.Secure.getString(
                        context?.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID,
                    )
                    if (!androidId.isNullOrBlank()) {
                        val md = java.security.MessageDigest.getInstance("MD5")
                        md.digest(androidId.toByteArray(Charsets.UTF_8))
                            .joinToString("") { "%02x".format(it) }
                    } else {
                        java.util.UUID.randomUUID().toString().replace("-", "")
                    }
                } catch (_: Exception) {
                    java.util.UUID.randomUUID().toString().replace("-", "")
                }
                Libv2ray.initCoreEnv(assetPath, deviceId)
                Log.i("CoreNative", "V2Ray core environment initialized successfully (deviceId=$deviceId)")
            } catch (e: Exception) {
                Log.e("CoreNative", "Failed to initialize V2Ray core environment", e)
                initialized.set(false)
                throw e
            }
        } else {
            Log.d("CoreNative", "V2Ray core environment already initialized, skipping")
        }
    }

    fun reconcileBrowserDialer(dialerAddr: String) {
        try {
            Libv2ray.reconcileBrowserDialer(dialerAddr)
            Log.i("CoreNative", "Browser dialer reconciled successfully with address: $dialerAddr")
        } catch (e: Exception) {
            Log.e("CoreNative", "Failed to reconcile browser dialer with address: $dialerAddr", e)
        }
    }

    /**
     * Get V2Ray core version.
     *
     * @return Version string of the V2Ray core
     */
    fun getLibVersion(): String {
        return try {
            Libv2ray.checkVersionX()
        } catch (e: Exception) {
            Log.e("CoreNative", "Failed to check V2Ray version", e)
            "Unknown"
        }
    }

    /**
     * Measure outbound connection delay.
     *
     * @param config The configuration JSON string
     * @param testUrl The URL to test against
     * @return Delay in milliseconds, or -1 if test failed
     */
    fun measureOutboundDelay(config: String, testUrl: String): Long {
        return try {
            Libv2ray.measureOutboundDelay(config, testUrl)
        } catch (e: Exception) {
            Log.e("CoreNative", "Failed to measure outbound delay", e)
            -1L
        }
    }

    /**
     * Create a new core controller instance.
     *
     * @param handler The callback handler for core events
     * @return A new CoreController instance
     */
    fun newCoreController(handler: CoreCallbackHandler): CoreController {
        return try {
            Libv2ray.newCoreController(handler)
        } catch (e: Exception) {
            Log.e("CoreNative", "Failed to create core controller", e)
            throw e
        }
    }
}