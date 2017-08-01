package moe.reimu.weiboxposed

// https://gist.github.com/davidwhitman/b83e1744e8435a2c8cba262c1179f1a8

import android.content.Context
import android.content.SharedPreferences
import com.crossbowffs.remotepreferences.RemotePreferences
import kotlin.reflect.KProperty

/**
 * Represents a single [SharedPreferences] file.
 *
 * Usage:
 *
 * `Preferences.init(context)`
 *
 * ```kotlin
 * class UserPreferences : Preferences() {
 *   var emailAccount by stringPref()
 *   var showSystemAppsPreference by booleanPref()
 * }
 * ```
 */
abstract class Preferences {
    companion object {
        private var context: Context? = null

        /**
         * Initialize PrefDelegate with a Context reference.
         *
         * **This method needs to be called before any other usage of PrefDelegate!!**
         */
        fun init(context: Context) {
            this.context = context
        }
    }

    private val prefs: RemotePreferences by lazy {
        if (context != null)
            RemotePreferences(context, PROVIDER_AUTHORITY, PREF_NAME)
        else
            throw IllegalStateException("Context was not initialized. Call Preferences.init(context) before using it")
    }


    abstract class PrefDelegate<out T>(val prefKey: String?) {
        abstract operator fun getValue(thisRef: Any?, property: KProperty<*>): T
    }

    fun stringPref(prefKey: String? = null, defaultValue: String? = null) = StringPrefDelegate(prefKey, defaultValue)

    inner class StringPrefDelegate(prefKey: String? = null, val defaultValue: String?) : PrefDelegate<String?>(prefKey) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String? = prefs.getString(prefKey ?: property.name, defaultValue)
    }

    fun intPref(prefKey: String? = null, defaultValue: Int = 0) = IntPrefDelegate(prefKey, defaultValue)

    inner class IntPrefDelegate(prefKey: String? = null, val defaultValue: Int) : PrefDelegate<Int>(prefKey) {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getInt(prefKey ?: property.name, defaultValue)
    }


    fun floatPref(prefKey: String? = null, defaultValue: Float = 0f) = FloatPrefDelegate(prefKey, defaultValue)

    inner class FloatPrefDelegate(prefKey: String? = null, val defaultValue: Float) : PrefDelegate<Float>(prefKey) {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getFloat(prefKey ?: property.name, defaultValue)
    }


    fun booleanPref(prefKey: String? = null, defaultValue: Boolean = false) = BooleanPrefDelegate(prefKey, defaultValue)

    inner class BooleanPrefDelegate(prefKey: String? = null, val defaultValue: Boolean) : PrefDelegate<Boolean>(prefKey) {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getBoolean(prefKey ?: property.name, defaultValue)
    }


    fun longPref(prefKey: String? = null, defaultValue: Long = 0L) = LongPrefDelegate(prefKey, defaultValue)

    inner class LongPrefDelegate(prefKey: String? = null, val defaultValue: Long) : PrefDelegate<Long>(prefKey) {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getLong(prefKey ?: property.name, defaultValue)
    }


    fun stringSetPref(prefKey: String? = null, defaultValue: Set<String> = HashSet<String>()) = StringSetPrefDelegate(prefKey, defaultValue)

    inner class StringSetPrefDelegate(prefKey: String? = null, val defaultValue: Set<String>) : PrefDelegate<Set<String>>(prefKey) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Set<String> = prefs.getStringSet(prefKey ?: property.name, defaultValue)
    }
}
