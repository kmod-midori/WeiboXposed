package moe.reimu.weiboxposed

import com.crossbowffs.remotepreferences.RemotePreferenceProvider


class SettingsProvider : RemotePreferenceProvider(PROVIDER_AUTHORITY, arrayOf(PREF_NAME)) {
    override fun checkAccess(prefName: String?, prefKey: String?, write: Boolean): Boolean {
        if (write) {
            return false
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            if (WB_PACKAGE_NAME != callingPackage) {
                return false
            }
        }
        return true
    }
}
