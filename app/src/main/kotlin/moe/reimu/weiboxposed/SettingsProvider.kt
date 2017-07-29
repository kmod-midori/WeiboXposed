package moe.reimu.weiboxposed

import com.crossbowffs.remotepreferences.RemotePreferenceProvider


class SettingsProvider : RemotePreferenceProvider(PROVIDER_AUTHORITY, arrayOf(PREF_NAME)) {
    override fun checkAccess(prefName: String?, prefKey: String?, write: Boolean): Boolean {
        return !write
    }
}
