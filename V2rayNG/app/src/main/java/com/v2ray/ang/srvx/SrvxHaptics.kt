package com.v2ray.ang.srvx

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Modern luxury haptic feedback controller for SRVX.
 * Provides tactile clicks, thumps, ticks, and confirmation vibrations.
 */
object SrvxHaptics {

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Subtle, crisp tick on selecting a server card or tapping a minor button.
     */
    fun tick(context: Context, view: View? = null) {
        view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        try {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10)
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Standard click feedback for action buttons and tabs.
     */
    fun click(context: Context, view: View? = null) {
        view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        try {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20)
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Heavy tactile thump for connecting or disconnecting the main VPN.
     */
    fun heavyClick(context: Context, view: View? = null) {
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        try {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(45)
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Double pulse pattern for successful connection confirmation.
     */
    fun success(context: Context) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 30, 70, 40)
                val amplitudes = intArrayOf(0, 180, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 30, 70, 40), -1)
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Error pattern for connection failure or timeout.
     */
    fun error(context: Context) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 50, 60, 50)
                val amplitudes = intArrayOf(0, 220, 0, 220)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 50, 60, 50), -1)
            }
        } catch (_: Throwable) {
        }
    }
}
