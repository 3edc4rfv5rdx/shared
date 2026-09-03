package dev.updater

/**
 * Everything an app has to say about its own updates.
 *
 * [appKey] is the folder the build publishes into on the server, so the manifest
 * for this app lives at "$baseUrl/$appKey/latest.json". It is not the package
 * name: the package is what the manifest itself carries, and the updater refuses
 * a manifest whose package is not this app's own.
 */
data class UpdaterConfig(
    val baseUrl: String,
    val appKey: String,
    /** Hours between two silent start-up checks. A manual check ignores this. */
    val checkIntervalHours: Int = 6,
)
