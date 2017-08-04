package moe.reimu.weiboxposed

import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceActivity
import android.os.Bundle
import android.widget.Toast

class SettingsActivity : PreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(this)
        fragmentManager.beginTransaction().replace(android.R.id.content, GeneralPreferenceFragment()).commit()
    }

    override fun isValidFragment(fragmentName: String): Boolean {
        return GeneralPreferenceFragment::class.java.name == fragmentName
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Toast.makeText(this, R.string.restart_weibo, Toast.LENGTH_SHORT).show()
        sendBroadcast(Intent(BROADCAST))
    }
}
