package com.v2ray.ang.srvx

import com.v2ray.ang.handler.MmkvManager

object AetherConfigManager {

    const val AETHER_REMARK_PREFIX = "Aether —"

    fun getScanMode(): String {
        return MmkvManager.decodeSettingsString("pref_aether_scan_mode") ?: "turbo"
    }

    fun setScanMode(mode: String) {
        MmkvManager.encodeSettings("pref_aether_scan_mode", mode)
    }

    fun getWarpKey(): String {
        return MmkvManager.decodeSettingsString("pref_aether_warp_key") ?: ""
    }

    fun setWarpKey(key: String) {
        MmkvManager.encodeSettings("pref_aether_warp_key", key.trim())
    }

    fun getZeroTrustTeam(): String {
        return MmkvManager.decodeSettingsString("pref_aether_zt_team") ?: ""
    }

    fun setZeroTrustTeam(team: String) {
        MmkvManager.encodeSettings("pref_aether_zt_team", team.trim())
    }

    fun getZeroTrustToken(): String {
        return MmkvManager.decodeSettingsString("pref_aether_zt_token") ?: ""
    }

    fun setZeroTrustToken(token: String) {
        MmkvManager.encodeSettings("pref_aether_zt_token", token.trim())
    }

    fun hasDedicatedLicense(): Boolean {
        return getWarpKey().isNotEmpty() ||
                (getZeroTrustTeam().isNotEmpty() && getZeroTrustToken().isNotEmpty())
    }

    suspend fun ensureFreeConfigs(forceRefresh: Boolean = false): Boolean {
        // We no longer generate free configs because ALL traffic goes through Aether via proxy chaining.
        return true
    }
}

