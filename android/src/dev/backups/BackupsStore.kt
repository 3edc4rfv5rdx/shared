package dev.backups

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.util.Log

/**
 * The one place that knows the copies live in MediaStore.
 *
 * `Documents/<dirName>` rather than the app's own storage: a backup that is
 * deleted together with the app it was meant to save is not a backup. The
 * folder is public, so the user can copy a file off the phone without the app,
 * and this is also why rotation only ever deletes files matching the app's own
 * name pattern — anything else in that folder was put there by the user.
 */
internal object BackupsStore {

    private const val TAG = "Backups"

    private val collection: Uri
        get() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private fun relativePath(config: BackupsConfig) = "${Environment.DIRECTORY_DOCUMENTS}/${config.dirName}"

    /**
     * Creates the file as pending, so nothing else sees a half-written archive.
     * The caller publishes it with [publish] or deletes it.
     */
    fun createPending(context: Context, config: BackupsConfig, name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, config.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(config))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(collection, values)
            ?: error("Cannot create backup file in ${relativePath(config)}")
    }

    /**
     * Makes a finished archive visible to everything else on the phone, and
     * answers what it ended up being called: MediaStore appends " (1)" when the
     * name is taken, which two copies made inside one second would be.
     */
    fun publish(context: Context, uri: Uri, fallbackName: String): String {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        check(context.contentResolver.update(uri, values, null, null) == 1) {
            "Cannot publish backup file"
        }
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: fallbackName
    }

    fun discard(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
            .onFailure { Log.w(TAG, "Cannot remove unfinished backup", it) }
    }

    /**
     * Keeps the newest [BackupsConfig.keepCount] copies and deletes the rest.
     *
     * Called only after a copy has been published: a failed backup must never
     * cost the oldest good one. Never throws — the new copy is already safe on
     * disk, and a folder that could not be tidied is not a failed backup. It
     * also cannot always succeed: after a reinstall the copies belong to the
     * previous install and the delete is refused, so they stay.
     *
     * An archive left pending by a killed process is neither counted nor
     * deleted here: a pending item is invisible to a plain query, even to the
     * app that created it, and the platform drops it after seven days.
     */
    fun prune(context: Context, config: BackupsConfig): Int {
        val resolver = context.contentResolver
        // The LIKE is a coarse prefilter and nothing more: SQL LIKE is case-insensitive and
        // its wildcard takes anything, so the pool is decided per row by isPoolName below.
        val pattern = likeLiteral(config.filePrefix) + "%" + likeLiteral(config.extension)
        // MediaStore normalises a stored path to a trailing slash; the value handed to insert
        // has none. Both spellings are asked for rather than matched with LIKE, which would
        // also catch a neighbouring "Documents/<dirName>Something".
        val path = relativePath(config)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} IN (?, ?) AND " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'"
        val args = arrayOf("$path/", path, pattern)

        val doomed = mutableListOf<Long>()
        try {
            resolver.query(
                collection,
                arrayOf(BaseColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                selection,
                args,
                // Newest first, and by id where two copies share a second — DATE_ADDED has no
                // finer resolution than that, and the name cannot be trusted to sort: a second
                // copy inside one second is stored as "… (1)".
                "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${BaseColumns._ID} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(BaseColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                var kept = 0
                while (cursor.moveToNext()) {
                    // A name of another shape was put here by the user, or by an app sharing
                    // the folder: neither counted against keepCount nor ever deleted.
                    if (!config.isPoolName(cursor.getString(nameColumn).orEmpty())) continue
                    if (kept < config.keepCount) kept++ else doomed += cursor.getLong(idColumn)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot list backups in $path", e)
            return 0
        }

        var deleted = 0
        for (id in doomed) {
            try {
                deleted += resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
            } catch (e: SecurityException) {
                // Written by a previous install of this app: MediaStore no longer counts it as
                // ours, and asking the user to confirm belongs nowhere near a silent backup.
                Log.w(TAG, "Old backup $id is not ours to delete", e)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot delete old backup $id", e)
            }
        }
        return deleted
    }

    /**
     * Escapes a literal for use inside a LIKE pattern. Not decoration: a prefix
     * such as "backup_" ends in a single-character wildcard, and unescaped it
     * would match — and rotate away — files belonging to a different pool.
     */
    private fun likeLiteral(text: String): String = text
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
