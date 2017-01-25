package moe.reimu.weiboxposed

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.support.v7.preference.PreferenceFragmentCompat
import android.widget.Toast
import java.net.URLEncoder

class GeneralPreferenceFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        val PREF_NAME = "prefs"
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Toast.makeText(context, R.string.restart_weibo, Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("WorldReadableFiles")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_WORLD_READABLE)
                .registerOnSharedPreferenceChangeListener(this)

        val manager = preferenceManager
        manager.sharedPreferencesMode = Context.MODE_WORLD_READABLE
        manager.sharedPreferencesName = PREF_NAME


        if (!isModuleEnabled()) {
            Toast.makeText(context, R.string.module_not_enabled, Toast.LENGTH_LONG).show()
        }

        addPreferencesFromResource(R.xml.pref_general)

        val pref_alipay = findPreference("alipay")
        pref_alipay.setOnPreferenceClickListener {
            val qrcode = URLEncoder.encode("https://qr.alipay.com/a6x04349f12rwyb6webwlb7", "utf-8")
            val alipayqr = "alipayqr://platformapi/startapp?saId=10000007&clientVersion=3.7.0.0718&qrcode=$qrcode"
            val uri = Uri.parse("$alipayqr&%3F_s%3Dweb-other&_t=${System.currentTimeMillis()}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            false
        }
    }

    private fun isModuleEnabled(): Boolean {
        return false
    }
}