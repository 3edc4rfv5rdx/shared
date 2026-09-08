package dev.updater

/**
 * Everything an app has to say about its own updates.
 *
 * Builds are published as GitHub release assets, and GitHub serves the newest
 * release under a fixed path — ".../releases/latest/download/<asset>" — so an
 * app names its repository once and never a version, a tag or an address again.
 */
data class UpdaterConfig(
    /**
     * The app's own short name. It names the download in the cache directory and
     * is the repository name when [repo] is left empty.
     */
    val appKey: String,
    /** The GitHub repository the releases live in, when it is not [appKey]. */
    val repo: String = "",
    /**
     * The account those repositories belong to, and the one place it is written:
     * every app here publishes to the same one.
     */
    val user: String = "3edc4rfv5rdx",
    /** A manifest hosted somewhere else entirely; used exactly as given. */
    val manifestUrl: String = "",
    /** Hours between two silent start-up checks. A manual check ignores this. */
    val checkIntervalHours: Int = 6,
) {
    /** The repository this app's releases live in — the name, not the address. */
    val repoName: String
        get() = repo.ifEmpty { appKey }

    /**
     * The repository's page. Written here rather than by every caller, because
     * this is where the account and the repository name already live together;
     * ../about puts it in front of the user.
     */
    val repoUrl: String
        get() = "https://github.com/$user/$repoName"

    /**
     * Where the manifest is read from. The APK urls inside it are relative to
     * this file, so the same manifest works from any host it is copied to.
     */
    val manifest: String
        get() = manifestUrl.ifEmpty {
            "$repoUrl/releases/latest/download/latest.json"
        }
}
