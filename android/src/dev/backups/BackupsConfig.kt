package dev.backups

import java.io.OutputStream

/**
 * Everything an app has to say about its own backups.
 *
 * The app owns what goes inside an archive and nothing else: when a copy is
 * made, what it is called, where it lands and how many are kept are decided
 * here, once, for every project.
 */
data class BackupsConfig(
    /**
     * The app's folder under Documents — "ReminderLists" means
     * `Documents/ReminderLists`. The same folder the app's own manual backup
     * writes to: auto and manual copies are one pool, and the retention below
     * counts both.
     */
    val dirName: String,
    /**
     * What every backup file of this app is called before the timestamp, and
     * with [extension] the definition of "our files": rotation deletes what
     * matches this pattern and leaves everything else in the folder alone.
     * Changing it in a released app orphans the copies made under the old one —
     * they stay on the phone and are never rotated again.
     */
    val filePrefix: String,
    val extension: String = ".zip",
    val mimeType: String = "application/zip",
    /** How many copies survive. The newest [keepCount] are kept, the rest deleted. */
    val keepCount: Int = 3,
    /**
     * Writes one complete archive into the given stream, throwing if it cannot.
     * Called on a background thread, never on the main one, and never twice at
     * the same time.
     *
     * Whatever wraps the stream must be finished before returning — a
     * ZipOutputStream that is not closed leaves a truncated archive, and a
     * truncated archive that is published would push a good copy out of the
     * pool. Closing the stream itself is fine; the module closes it again and
     * a second close does nothing.
     */
    val write: (OutputStream) -> Unit,
) {
    init {
        require(dirName.isNotBlank() && !dirName.contains('/')) { "dirName must be one folder name" }
        require(filePrefix.isNotBlank()) { "filePrefix must not be blank" }
        require(extension.startsWith(".")) { "extension must start with a dot" }
        require(keepCount >= 1) { "keepCount must be at least 1" }
    }
}
