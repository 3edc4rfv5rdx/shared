package dev.updater

import org.json.JSONObject

/**
 * One published build, as described by latest.json.
 *
 * [versionCode] is the only field an update decision is made on: it is the
 * number the build scripts already own (build_number.txt / the "+N" in
 * pubspec.yaml), and Android itself refuses to install a lower one over a
 * higher one. [versionName] is for the dialog to show and nothing else.
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
        /** Returns null when the document is not a manifest we can act on. */
        fun parse(json: String, baseUrl: String, appKey: String): UpdateInfo? {
            val o = try {
                JSONObject(json)
            } catch (e: Exception) {
                return null
            }

            val packageName = o.optString("package")
            val versionName = o.optString("versionName")
            val versionCode = o.optLong("versionCode", -1L)
            val url = o.optString("url")
            val sha256 = o.optString("sha256").lowercase()

            if (packageName.isEmpty() || versionName.isEmpty() ||
                versionCode <= 0L || url.isEmpty() || sha256.length != 64
            ) {
                return null
            }

            // A relative url keeps the manifest independent of how the server is
            // reached: the same file works from a LAN address and from a name.
            val absoluteUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                url
            } else {
                "${baseUrl.trimEnd('/')}/$appKey/${url.trimStart('/')}"
            }

            return UpdateInfo(
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                abi = o.optString("abi", "arm64-v8a"),
                url = absoluteUrl,
                size = o.optLong("size", -1L),
                sha256 = sha256,
            )
        }
    }
}
