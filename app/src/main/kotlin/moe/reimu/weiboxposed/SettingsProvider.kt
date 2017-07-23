package moe.reimu.weiboxposed

import android.content.*
import android.database.Cursor
import android.net.Uri
import android.database.MatrixCursor



class SettingsProvider : ContentProvider() {
    companion object {
        val INT_TYPE = "integer"
        val LONG_TYPE = "long"
        val FLOAT_TYPE = "float"
        val BOOLEAN_TYPE = "boolean"
        val STRING_TYPE = "string"

        private val matcher = UriMatcher(UriMatcher.NO_MATCH)
        private val MATCH_DATA = 0x010000
    }

    lateinit private var prefs: SharedPreferences

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        return 0
    }

    override fun getType(uri: Uri): String? {
        return ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd." + PROVIDER_AUTHORITY + ".item"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun onCreate(): Boolean {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        matcher.addURI(PROVIDER_AUTHORITY, "*/*", MATCH_DATA)

        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        when (matcher.match(uri)) {
            MATCH_DATA -> {
                val key = uri.pathSegments[0]
                val type = uri.pathSegments[1]
                val cursor = MatrixCursor(arrayOf(key))
                if (!prefs.contains(key)) return cursor

                val rowBuilder = cursor.newRow()
                val obj = when (type) {
                    BOOLEAN_TYPE -> prefs.getBoolean(key, false).to(Int)
                    STRING_TYPE -> prefs.getString(key, "")
                    LONG_TYPE -> prefs.getLong(key, 0)
                    INT_TYPE -> prefs.getInt(key, 0)
                    FLOAT_TYPE -> prefs.getFloat(key, 0f)
                    else -> throw IllegalArgumentException("Unsupported type $uri")
                }
                rowBuilder.add(obj)
                return cursor
            }
            else -> throw IllegalArgumentException("Unsupported uri $uri")
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<String>?): Int {
        return 0
    }
}
