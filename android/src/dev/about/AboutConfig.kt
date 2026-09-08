package dev.about

import dev.updater.UpdaterConfig

/**
 * Everything an app has to say about itself.
 *
 * Almost nothing, on purpose: the name and the version are read off the
 * installed package, the repository address comes out of the updater's own
 * config, and the account and the mailbox are the same in every app here. What
 * is left is the build date, which no API knows, and whatever one app wants to
 * show that the others do not.
 */
data class AboutConfig(
    /**
     * The app's update settings, already written for [dev.updater.Updater]. The
     * repository address on screen and the Update button both come from it.
     */
    val updater: UpdaterConfig,
    /**
     * The day this build was made, as the build script wrote it into
     * BuildConfig.BUILD_DATE. Empty hides the line — a build made from an
     * unbumped tree has no date to show.
     */
    val buildDate: String = "",
    /** The mailbox every app here is reached at. */
    val email: String = "3edc4rfv5rdx@gmail.com",
    /** Extra lines, label to value, shown under the ones every app has. */
    val extra: List<Pair<String, String>> = emptyList(),
    /**
     * Whether the dialog offers a check for updates. On by default, because an
     * app that reads its own releases can always be asked to look again; an app
     * whose builds are not published sets it false.
     */
    val offerUpdate: Boolean = true,
)
