package com.v2ray.ang.srvx

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

/**
 * Quick dark/light toggle used from the navigation drawer.
 * Uses the SAME pref key as the Settings screen (pref_ui_mode_night) so the
 * two stay in sync. Values: "1" = light, "2" = dark (matches ui_mode_night_value).
 */
object SrvxTheme {
    fun toggle(activity: Activity) {
        val current = MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "2")
        val next = if (current == "2") "1" else "2"   // dark <-> light
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, next)

        AppCompatDelegate.setDefaultNightMode(
            if (next == "2") AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        val label = if (next == "2") "تم تیره" else "تم روشن"
        Toast.makeText(activity, label, Toast.LENGTH_SHORT).show()
    }
}
