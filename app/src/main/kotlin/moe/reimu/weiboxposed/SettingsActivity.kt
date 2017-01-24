package moe.reimu.weiboxposed

import android.support.v4.app.Fragment

class SettingsActivity : SingleFragmentActivity() {
    override fun createFragment(): Fragment {
        return GeneralPreferenceFragment()
    }
}
