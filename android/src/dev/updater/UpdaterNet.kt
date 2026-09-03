package dev.updater

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The two network calls the updater makes, on HttpURLConnection and nothing else:
 * adding a HTTP client to eight projects for two requests is not a trade worth
 * making, and the Flutter projects carry no coroutines to build it on either.
 *
 * Both run on a caller-supplied background thread and neither touches the UI.
 */
internal object UpdaterNet {

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 15_000

    /** The manifest body, or null when the server did not answer with one. */
    fun fetchManifest(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                // The manifest is rewritten in place on every publish, so a cached
                // copy is exactly the answer that must not be used here.
                useCaches = false
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Downloads [url] into [target] and returns its SHA-256, or null if the
     * transfer failed or [isCancelled] went true part-way through.
     *
     * The digest is computed over the bytes as they arrive, so verifying the
     * download costs no second pass over the file.
     */
    fun download(
        url: String,
        target: File,
        expectedSize: Long,
        isCancelled: () -> Boolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): String? {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            // Content-Length is the honest total; the manifest's size is only a
            // fallback for a chunked response, and 0 means "no bar, just spin".
            val declared = connection.contentLength.toLong()
            val total = if (declared > 0L) declared else expectedSize

            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L

            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled()) return null
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                    output.fd.sync()
                }
            }

            return digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return null
        } finally {
            connection?.disconnect()
        }
    }
}
