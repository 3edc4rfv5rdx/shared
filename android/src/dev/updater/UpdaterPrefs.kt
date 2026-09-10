package dev.updater

import android.content.Context

/**
 * What the updater has to remember between launches: whether the start-up check
 * is wanted at all, when it last looked, and which build the user turned down.
 *
 * Its own preferences file, so it never collides with a key the host app owns.
 */
internal class UpdaterPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("dev.updater", Context.MODE_PRIVATE)

    /** Whether the start-up check runs. The settings switch reads and writes this. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var lastCheckAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    /** The build the user turned down with Cancel; 0 when there is none. */
    var declinedVersionCode: Long
        get() = prefs.getLong(KEY_DECLINED, 0L)
        set(value) = prefs.edit().putLong(KEY_DECLINED, value).apply()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_LAST_CHECK = "last_check_at"
        const val KEY_DECLINED = "declined_version_code"
    }
}
