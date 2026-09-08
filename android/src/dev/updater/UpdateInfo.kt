package dev.updater

import org.json.JSONObject

/**
 * One published build, as described by latest.json.
 *
 * [versionCode] is the only field an update decision is made on, and it belongs
 * to the build actually chosen for this device: under Flutter's --split-per-abi
 * the same source build carries a different code in every split, so the number
 * lives beside the url rather than above it. [versionName] is for the dialog to
 * show and nothing else. [abi] says which split was picked.
 */
data class UpdateInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val abi: String,
    val url: String,
    val size: Long,
    val sha256: String,
) {
    companion object {

        /** The build that runs anywhere, and the last thing tried. */
        private const val UNIVERSAL = "universal"

        /**
         * Picks the build for a device whose [supportedAbis] are given in the
         * device's own order of preference — `Build.SUPPORTED_ABIS`, where a
         * 64-bit phone lists arm64-v8a before the armeabi-v7a it can also run.
         * Walking that list rather than the manifest is what makes the phone take
         * the arm64 split and the TV box the v7a one out of the same file.
         */
        fun parse(
            json: String,
            manifestUrl: String,
            supportedAbis: List<String>,
        ): ManifestResult {
            val o = try {
                JSONObject(json)
            } catch (e: Exception) {
                return ManifestResult.Invalid
            }

            val packageName = o.optString("package")
            val versionName = o.optString("versionName")
            if (packageName.isEmpty() || versionName.isEmpty()) return ManifestResult.Invalid

            // The code above the map is what a flat manifest carries and what a
            // split entry falls back to; a split that names its own wins.
            val sharedCode = o.optLong("versionCode", -1L)

            val builds = o.optJSONObject("builds")
            val abi: String
            val build: JSONObject

            if (builds != null && builds.length() > 0) {
                val picked = supportedAbis.firstOrNull { builds.has(it) }
                    ?: UNIVERSAL.takeIf { builds.has(it) }
                    ?: return ManifestResult.NoBuildForDevice
                abi = picked
                build = builds.optJSONObject(picked) ?: return ManifestResult.Invalid
            } else {
                // The flat form: one build for everyone, which is what every
                // manifest looked like before the splits were published.
                abi = o.optString("abi")
                build = o
            }

            val versionCode = build.optLong("versionCode", sharedCode)
            val url = build.optString("url")
            val size = build.optLong("size", -1L)
            val sha256 = build.optString("sha256").lowercase()

            if (versionCode <= 0L || url.isEmpty() || sha256.length != 64) {
                return ManifestResult.Invalid
            }

            // A relative url is read beside the manifest itself, which is where
            // a release keeps its assets: the same file works whatever host it
            // was copied to, and survives the next tag without being rewritten.
            val absoluteUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                url
            } else {
                "${manifestUrl.substringBeforeLast('/')}/${url.trimStart('/')}"
            }

            return ManifestResult.Ok(
                UpdateInfo(
                    packageName = packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    abi = abi,
                    url = absoluteUrl,
                    size = size,
                    sha256 = sha256,
                )
            )
        }
    }
}

/** What a manifest turned out to be, told apart because the answer differs. */
sealed class ManifestResult {
    data class Ok(val info: UpdateInfo) : ManifestResult()

    /**
     * A manifest this app can read, publishing nothing this device can run: an
     * arm64-only folder seen from an armeabi-v7a box. Silent on a start-up check
     * and its own message on a manual one — "server unreachable" would send the
     * user looking in the wrong place.
     */
    object NoBuildForDevice : ManifestResult()

    /** Not a manifest, or one missing what an install needs. */
    object Invalid : ManifestResult()
}
