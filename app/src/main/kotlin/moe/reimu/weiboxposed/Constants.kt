package moe.reimu.weiboxposed

import android.net.Uri

const val PREF_NAME = "prefs"
const val PROVIDER_AUTHORITY = "moe.reimu.weiboxposed.provider"
val PREF_BASE_URI : Uri = Uri.parse("content://" + PROVIDER_AUTHORITY)