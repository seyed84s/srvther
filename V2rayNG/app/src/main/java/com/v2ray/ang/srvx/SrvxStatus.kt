package com.v2ray.ang.srvx

import android.app.Activity
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.TextView
import com.v2ray.ang.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SrvxStatus {

    fun refresh(activity: Activity) {
        val dataView = activity.findViewById<TextView>(R.id.srvx_data_value) ?: return
        val timeView = activity.findViewById<TextView>(R.id.srvx_time_value) ?: return
        val refreshBtn = activity.findViewById<ImageView>(R.id.srvx_btn_refresh_status)

        dataView.text = "نامحدود"
        timeView.text = if (AetherConfigManager.hasDedicatedLicense()) "WARP+ فعال 💎" else "رایگان Aether 🌐"
        if (refreshBtn != null) {
            val rotate = RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
                duration = 500
                repeatCount = 0
            }
            refreshBtn.startAnimation(rotate)
            refreshBtn.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    AetherConfigManager.ensureFreeConfigs(forceRefresh = true)
                    refresh(activity)
                }
            }
        }
    }
}
