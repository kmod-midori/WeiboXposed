package moe.reimu.weiboxposed

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceFragment
import android.widget.Toast
import java.net.URLEncoder

class GeneralPreferenceFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = preferenceManager
        manager.sharedPreferencesName = PREF_NAME


        if (!isModuleEnabled()) {
            Toast.makeText(activity, R.string.module_not_enabled, Toast.LENGTH_LONG).show()
        }

        addPreferencesFromResource(R.xml.pref_general)

        val pref_alipay = findPreference("alipay")
        pref_alipay.setOnPreferenceClickListener {
            val qrcode = URLEncoder.encode("https://qr.alipay.com/a6x04349f12rwyb6webwlb7", "utf-8")
            val alipayqr = "alipayqr://platformapi/startapp?saId=10000007&clientVersion=3.7.0.0718&qrcode=$qrcode"
            val uri = Uri.parse("$alipayqr&%3F_s%3Dweb-other&_t=${System.currentTimeMillis()}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            try {
                activity.startActivity(intent)
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