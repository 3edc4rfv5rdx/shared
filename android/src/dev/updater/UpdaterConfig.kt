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
    val appKey: String,
    /**
     * The home server, and the one place its address is written: every app here
     * checks the same machine, so moving it must not mean editing eight apps.
     * An app on another network passes its own.
     */
    val baseUrl: String = "http://192.168.54.250:8081",
    /** Hours between two silent start-up checks. A manual check ignores this. */
    val checkIntervalHours: Int = 6,
)
