package dev.backups

import android.content.Context

/**
 * What the scheduler has to remember between launches: whether the user wants
 * daily copies at all, when the last one was actually written, and when the
 * last attempt was made.
 *
 * The two timestamps are not the same question. [lastRunAt] answers "is there a
 * copy for today"; [lastAttemptAt] answers "has this already failed recently",
 * and without it a phone with no free space would retry on every single launch.
 *
 * Its own preferences file, so it never collides with a key the host app owns.
 */
internal class BackupsPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("dev.backups", Context.MODE_PRIVATE)

    /** On unless the user turned it off: an app that holds data backs it up by default. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** When a copy was last written, auto or manual; 0 when there has never been one. */
    var lastRunAt: Long
        get() = prefs.getLong(KEY_LAST_RUN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_RUN, value).apply()

    /** When a copy was last attempted, whether or not it worked. */
    var lastAttemptAt: Long
        get() = prefs.getLong(KEY_LAST_ATTEMPT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_ATTEMPT, value).apply()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_LAST_RUN = "last_run_at"
        const val KEY_LAST_ATTEMPT = "last_attempt_at"
    }
}
