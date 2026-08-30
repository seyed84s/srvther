package com.v2ray.ang.srvx

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import kotlinx.coroutines.launch

class AetherSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aether_settings)

        val btnBack = findViewById<ImageView>(R.id.btn_back)
        val btnSaveTop = findViewById<ImageView>(R.id.btn_save_top)
        val btnSaveBottom = findViewById<TextView>(R.id.btn_save_bottom)
        val badgeStatus = findViewById<TextView>(R.id.tv_account_status_badge)
        val etWarpKey = findViewById<EditText>(R.id.et_warp_key)
        val etZtTeam = findViewById<EditText>(R.id.et_zt_team)
        val etZtToken = findViewById<EditText>(R.id.et_zt_token)
        val btnOpenPortal = findViewById<TextView>(R.id.btn_open_free_portal)
        val rgScanMode = findViewById<RadioGroup>(R.id.rg_scan_mode)
        val rbTurbo = findViewById<RadioButton>(R.id.rb_scan_turbo)
        val rbBalanced = findViewById<RadioButton>(R.id.rb_scan_balanced)
        val rbThorough = findViewById<RadioButton>(R.id.rb_scan_thorough)
        val btnRebuild = findViewById<TextView>(R.id.btn_rebuild_configs)

        val switchChain = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_aether_chain)
        val rgChainMode = findViewById<RadioGroup>(R.id.rg_chain_mode)
        val rbChainMasque = findViewById<RadioButton>(R.id.rb_chain_masque)
        val rbChainWg = findViewById<RadioButton>(R.id.rb_chain_wg)
        val rbChainGool = findViewById<RadioButton>(R.id.rb_chain_gool)

        btnBack.setOnClickListener {
            SrvxHaptics.tick(this, btnBack)
            finish()
        }

        // Load current values
        etWarpKey.setText(AetherConfigManager.getWarpKey())
        etZtTeam.setText(AetherConfigManager.getZeroTrustTeam())
        etZtToken.setText(AetherConfigManager.getZeroTrustToken())

        updateStatusBadge(badgeStatus)

        when (AetherConfigManager.getScanMode()) {
            "balanced" -> rbBalanced.isChecked = true
            "thorough" -> rbThorough.isChecked = true
            else -> rbTurbo.isChecked = true
        }

        switchChain.isChecked = com.v2ray.ang.handler.MmkvManager.decodeSettingsBool(com.v2ray.ang.AppConfig.PREF_AETHER_CHAIN_ENABLED, true) == true
        when (com.v2ray.ang.handler.MmkvManager.decodeSettingsString(com.v2ray.ang.AppConfig.PREF_AETHER_CHAIN_MODE)) {
            "wg" -> rbChainWg.isChecked = true
            "gool" -> rbChainGool.isChecked = true
            else -> rbChainMasque.isChecked = true
        }

        val saveAction = {
            SrvxHaptics.success(this)
            val key = etWarpKey.text.toString().trim()
            val team = etZtTeam.text.toString().trim()
            val token = etZtToken.text.toString().trim()

            AetherConfigManager.setWarpKey(key)
            AetherConfigManager.setZeroTrustTeam(team)
            AetherConfigManager.setZeroTrustToken(token)

            val mode = when (rgScanMode.checkedRadioButtonId) {
                R.id.rb_scan_balanced -> "balanced"
                R.id.rb_scan_thorough -> "thorough"
                else -> "turbo"
            }
            AetherConfigManager.setScanMode(mode)

            val chainEnabled = switchChain.isChecked
            val chainMode = when (rgChainMode.checkedRadioButtonId) {
                R.id.rb_chain_wg -> "wg"
                R.id.rb_chain_gool -> "gool"
                else -> "masque"
            }
            com.v2ray.ang.handler.MmkvManager.encodeSettings(com.v2ray.ang.AppConfig.PREF_AETHER_CHAIN_ENABLED, chainEnabled)
            com.v2ray.ang.handler.MmkvManager.encodeSettings(com.v2ray.ang.AppConfig.PREF_AETHER_CHAIN_MODE, chainMode)

            // Rebuild/update free configs with new settings (async)
            lifecycleScope.launch {
                val ok = AetherConfigManager.ensureFreeConfigs(forceRefresh = true)
                updateStatusBadge(badgeStatus)
                if (ok) {
                    Toast.makeText(this@AetherSettingsActivity,
                        "تنظیمات Aether با موفقیت ذخیره و اعمال شد ✨", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AetherSettingsActivity,
                        "ذخیره شد ولی ثبت‌نام کلودفلر ناموفق بود — اینترنت را بررسی کنید", Toast.LENGTH_LONG).show()
                }
                finish()
            }
            Unit
        }

        btnSaveTop.setOnClickListener { saveAction() }
        btnSaveBottom.setOnClickListener { saveAction() }

        btnOpenPortal.setOnClickListener {
            SrvxHaptics.click(this, btnOpenPortal)
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://one.dash.cloudflare.com"))
                startActivity(browserIntent)
            } catch (_: Throwable) {
                Toast.makeText(this, "مرورگری برای باز کردن پورتال یافت نشد", Toast.LENGTH_SHORT).show()
            }
        }

        btnRebuild.setOnClickListener {
            SrvxHaptics.click(this, btnRebuild)
            btnRebuild.isEnabled = false
            btnRebuild.alpha = 0.5f
            lifecycleScope.launch {
                val ok = AetherConfigManager.ensureFreeConfigs(forceRefresh = true)
                btnRebuild.isEnabled = true
                btnRebuild.alpha = 1f
                if (ok) {
                    Toast.makeText(this@AetherSettingsActivity,
                        "تنظیمات اعمال شد 🚀", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AetherSettingsActivity,
                        "خطا در ارتباط با سرور — لطفاً دوباره تلاش کنید", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateStatusBadge(badge: TextView) {
        if (AetherConfigManager.hasDedicatedLicense()) {
            badge.text = "لایسنس اختصاصی فعال 💎"
            badge.setTextColor(ContextCompat.getColor(this, R.color.srvx_gold))
            badge.setBackgroundResource(R.drawable.srvx_pill_ping_amber)
        } else {
            badge.text = "رایگان عمومی"
            badge.setTextColor(ContextCompat.getColor(this, R.color.srvx_emerald))
            badge.setBackgroundResource(R.drawable.srvx_pill_ping_green)
        }
    }
}
