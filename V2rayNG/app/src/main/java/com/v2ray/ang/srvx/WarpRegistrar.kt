package com.v2ray.ang.srvx

import android.util.Base64
import com.v2ray.ang.handler.MmkvManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class WarpAccount(
    val privateKey: String,
    val publicKey: String,
    val localAddressV4: String,
    val localAddressV6: String,
    val reserved: String,
    val token: String,
    val id: String
)

/**
 * Handles genuine Cloudflare WARP device registration via the public API.
 * Registration is fully automated and invisible to the user.
 */
object WarpRegistrar {

    private const val PREF_WARP_PRIV_KEY = "pref_warp_priv_key"
    private const val PREF_WARP_PUB_KEY = "pref_warp_pub_key"
    private const val PREF_WARP_V4 = "pref_warp_v4"
    private const val PREF_WARP_V6 = "pref_warp_v6"
    private const val PREF_WARP_RESERVED = "pref_warp_reserved"
    private const val PREF_WARP_TOKEN = "pref_warp_token"
    private const val PREF_WARP_ID = "pref_warp_id"

    fun getSavedAccount(): WarpAccount? {
        val priv = MmkvManager.decodeSettingsString(PREF_WARP_PRIV_KEY) ?: return null
        if (priv.length < 40) return null // Invalid key, too short for 32 bytes base64
        val pub = MmkvManager.decodeSettingsString(PREF_WARP_PUB_KEY) ?: return null
        val v4 = MmkvManager.decodeSettingsString(PREF_WARP_V4) ?: "172.16.0.2/32"
        val v6 = MmkvManager.decodeSettingsString(PREF_WARP_V6)
            ?: "2606:4700:110:8f55:d46:513c:db6a:8a22/128"
        val res = MmkvManager.decodeSettingsString(PREF_WARP_RESERVED) ?: return null
        if (res == "0,0,0") return null // Never got real reserved bytes → re-register
        val tok = MmkvManager.decodeSettingsString(PREF_WARP_TOKEN) ?: ""
        val id = MmkvManager.decodeSettingsString(PREF_WARP_ID) ?: ""
        return WarpAccount(priv, pub, v4, v6, res, tok, id)
    }

    fun saveAccount(acc: WarpAccount) {
        MmkvManager.encodeSettings(PREF_WARP_PRIV_KEY, acc.privateKey)
        MmkvManager.encodeSettings(PREF_WARP_PUB_KEY, acc.publicKey)
        MmkvManager.encodeSettings(PREF_WARP_V4, acc.localAddressV4)
        MmkvManager.encodeSettings(PREF_WARP_V6, acc.localAddressV6)
        MmkvManager.encodeSettings(PREF_WARP_RESERVED, acc.reserved)
        MmkvManager.encodeSettings(PREF_WARP_TOKEN, acc.token)
        MmkvManager.encodeSettings(PREF_WARP_ID, acc.id)
    }

    /** Clears saved account so next register() creates a fresh one. */
    fun clearAccount() {
        MmkvManager.encodeSettings(PREF_WARP_PRIV_KEY, null as String?)
        MmkvManager.encodeSettings(PREF_WARP_PUB_KEY, null as String?)
        MmkvManager.encodeSettings(PREF_WARP_V4, null as String?)
        MmkvManager.encodeSettings(PREF_WARP_V6, null as String?)
        MmkvManager.encodeSettings(PREF_WARP_RESERVED, null as String?)
        MmkvManager.encodeSettings(PREF_WARP_TOKEN, null as String?)
        MmkvManager.encodeSettings(PREF_WARP_ID, null as String?)
    }

    /**
     * Registers a genuine Cloudflare WARP device.
     * Returns a cached account if valid, otherwise performs a fresh registration.
     * This is a BLOCKING call — must be called from a background thread.
     */
    fun register(): WarpAccount? {
        // Return existing cached valid account if available
        getSavedAccount()?.let { return it }

        return try {
            val privBytes = Curve25519.generatePrivateKey()
            val pubBytes = Curve25519.eval(privBytes)
            val privBase64 = Base64.encodeToString(privBytes, Base64.NO_WRAP)
            val pubBase64 = Base64.encodeToString(pubBytes, Base64.NO_WRAP)

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val nowIso = sdf.format(Date())

            val body = JSONObject().apply {
                put("install_id", "")
                put("tos", nowIso)
                put("key", pubBase64)
                put("fcm_token", "")
                put("type", "Android")
                put("locale", "en_US")
            }

            val conn = (URL("https://api.cloudflareclient.com/v0a2158/reg")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "okhttp/3.12.1")
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val responseText = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            conn.disconnect()

            if (code in 200..299 && responseText.isNotEmpty()) {
                val json = JSONObject(responseText)
                val id = json.optString("id", "")
                val token = json.optString("token", "")
                val config = json.optJSONObject("config")
                val iface = config?.optJSONObject("interface")
                val addrs = iface?.optJSONObject("addresses")
                val v4 = addrs?.optString("v4", "172.16.0.2/32") ?: "172.16.0.2/32"
                val v6 = addrs?.optString("v6", "2606:4700:110:8f55:d46:513c:db6a:8a22/128")
                    ?: "2606:4700:110:8f55:d46:513c:db6a:8a22/128"

                // Extract reserved bytes from client_id (base64-encoded 3 bytes)
                val reserved = extractReservedFromClientId(config)

                val account = WarpAccount(
                    privateKey = privBase64,
                    publicKey = pubBase64,
                    localAddressV4 = v4,
                    localAddressV6 = v6,
                    reserved = reserved,
                    token = token,
                    id = id
                )
                saveAccount(account)
                account
            } else {
                null // Registration failed — don't return garbage
            }
        } catch (e: Throwable) {
            null // Network error — caller will handle
        }
    }

    /**
     * Extracts the 3 reserved bytes from the `client_id` field in the WARP API response.
     * `client_id` is a Base64-encoded 3-byte value.
     */
    private fun extractReservedFromClientId(config: JSONObject?): String {
        return try {
            val clientId = config?.optString("client_id", "") ?: ""
            if (clientId.isNotEmpty()) {
                val bytes = Base64.decode(clientId, Base64.DEFAULT)
                if (bytes.size >= 3) {
                    "${bytes[0].toInt() and 0xFF},${bytes[1].toInt() and 0xFF},${bytes[2].toInt() and 0xFF}"
                } else {
                    "0,0,0"
                }
            } else {
                "0,0,0"
            }
        } catch (_: Throwable) {
            "0,0,0"
        }
    }
}
