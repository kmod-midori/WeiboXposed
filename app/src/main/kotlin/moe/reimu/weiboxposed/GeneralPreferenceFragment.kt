package moe.reimu.weiboxposed

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.preference.PreferenceFragmentCompat
import android.widget.Toast

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
    }

    private fun isModuleEnabled(): Boolean {
        return false
    }
}