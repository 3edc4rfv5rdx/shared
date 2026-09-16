package dev.updater

import android.content.Context

/**
 * What the updater has to remember between launches: whether the start-up check
 * is wanted at all, which build it last put in front of the user and when, and
 * which build the user turned down.
 *
 * Nothing here records a look at the server. Asking costs a manifest under a
 * kilobyte and is throttled to once per process in memory; what is worth
 * remembering across launches is what the user was shown, because that is what
 * can become nagging.
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

    /**
     * The build last offered and answered, and when — the pair the start-up
     * check throttles on. A build with any other version code is a build the
     * user has not seen, and is offered at once however recent this is.
     */
    var lastOfferedVersionCode: Long
        get() = prefs.getLong(KEY_OFFERED_CODE, 0L)
        set(value) = prefs.edit().putLong(KEY_OFFERED_CODE, value).apply()

    var lastOfferedAt: Long
        get() = prefs.getLong(KEY_OFFERED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_OFFERED_AT, value).apply()

    /** The build the user turned down with Cancel; 0 when there is none. */
    var declinedVersionCode: Long
        get() = prefs.getLong(KEY_DECLINED, 0L)
        set(value) = prefs.edit().putLong(KEY_DECLINED, value).apply()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_OFFERED_CODE = "last_offered_version_code"
        const val KEY_OFFERED_AT = "last_offered_at"
        const val KEY_DECLINED = "declined_version_code"
    }
}
