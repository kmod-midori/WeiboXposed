package moe.reimu.weiboxposed

import android.content.Context
import android.database.Cursor


class RemoteSharedPreference(val context: Context) {
    fun getString(key: String, def: String): String {
        val cursor = query(key, SettingsProvider.STRING_TYPE) ?: return def

        var value = def
        if (cursor.moveToFirst()) {
            value = cursor.getString(0)
        }
        cursor.close()
        return value
    }

    fun getBoolean(key: String, def: Boolean): Boolean {
        val cursor = query(key, SettingsProvider.BOOLEAN_TYPE) ?: return def

        var value = def
        if (cursor.moveToFirst()) {
            value = cursor.getInt(0) > 0
        }
        cursor.close()
        return value
    }

    private fun query(key: String, type: String): Cursor? {
        val uri = PREF_BASE_URI.buildUpon().appendPath(key).appendPath(type).build()
        return context.contentResolver.query(uri, null, null, null, null)
    }
}