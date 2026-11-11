package dev.about

/**
 * Everything an app has to say about itself.
 *
 * Almost nothing, on purpose: the name and the version are read off the
 * installed package, and the account and the mailbox are the same in every app
 * here. What is left is the repository, the build date, which no API knows,
 * and whatever one app wants to show that the others do not.
 */
data class AboutConfig(
    /** The GitHub repository the app lives in — the name, not the address. */
    val repo: String,
    /**
     * The day this build was made, as the build script wrote it into
     * BuildConfig.BUILD_DATE. Empty hides the line — a build made from an
     * unbumped tree has no date to show.
     */
    val buildDate: String = "",
    /**
     * Where the update button gets its answers: ../updater's AboutUpdater in a
     * build that carries the updater. Null — a store build, or an app whose
     * builds are not published — and there is no button: a check that cannot
     * answer is worse than none.
     */
    val updates: AboutUpdates? = null,
    /** The account every repository here belongs to. */
    val user: String = "3edc4rfv5rdx",
    /** The mailbox every app here is reached at. */
    val email: String = "3edc4rfv5rdx@gmail.com",
    /**
     * One sentence about what the app is, shown above the facts. Empty in an app
     * whose name already says it.
     */
    val description: String = "",
    /** Extra lines, label to value, shown under the ones every app has. */
    val extra: List<Pair<String, String>> = emptyList(),
) {
    /** The repository's page, shown as a link. */
    val repoUrl: String
        get() = "https://github.com/$user/$repo"
}
