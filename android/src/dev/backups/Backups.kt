package dev.backups

import android.content.Context
import android.util.Log
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Semaphore

/**
 * One copy of the database a day, three copies kept.
 *
 * The app says what an archive contains; everything else — when, where, under
 * what name, and how many survive — is decided here for every project at once.
 *
 * There is no scheduler and no background job. The check belongs in the start-up
 * path of an activity or a service, where it costs one short-lived thread and
 * reads nothing on the caller's: an app whose data is not changing is an app
 * nobody is opening.
 *
 * One app, one pool. The flag and the timestamps are a single set per app, so an
 * app that wanted two independent backup sets would need more than one config.
 */
object Backups {

    private const val TAG = "Backups"

    /**
     * How long a failed attempt keeps the next one away. Without it a phone with
     * no free space would rewrite the same failure on every launch, and every
     * launch would carry the cost of building the archive first.
     */
    private const val RETRY_INTERVAL_MS = 60L * 60L * 1000L

    /**
     * One backup at a time in a process, whichever entry point asked for it.
     *
     * A semaphore and not a lock: [checkOnStart] takes it on the caller's thread
     * and gives it back on the worker's, which a ReentrantLock — owned by the
     * thread that locked it — refuses to allow.
     */
    private val gate = Semaphore(1)

    /**
     * The daily check, for `onCreate` of the launcher activity and of any
     * service that records data while the app is closed. Returns at once: the
     * work, if there is any, happens on its own thread.
     *
     * Silent by design — the user hears about a backup in the settings screen,
     * not in a dialog on the way into the app.
     */
    fun checkOnStart(context: Context, config: BackupsConfig) {
        val app = context.applicationContext
        // Nothing is read on the caller's thread, not even the flag: SharedPreferences is a
        // file, and the start-up path of an activity is no place to open one. Taking the
        // permit is the whole of it — an activity and its service coming up together give
        // one worker, not two.
        if (!gate.tryAcquire()) return

        val worker = Thread({
            try {
                val prefs = BackupsPrefs(app)
                val now = System.currentTimeMillis()
                if (isDue(prefs, now)) {
                    prefs.lastAttemptAt = now
                    perform(app, config, prefs, now)
                        .onFailure { Log.w(TAG, "Daily backup failed", it) }
                }
            } catch (e: Throwable) {
                // An exception left uncaught here would take the app down with it.
                Log.w(TAG, "Daily backup check failed", e)
            } finally {
                gate.release()
            }
        }, "backups")

        try {
            worker.start()
        } catch (e: Throwable) {
            // Nothing will reach that finally if the thread never ran.
            gate.release()
            Log.w(TAG, "Cannot start the backup thread", e)
        }
    }

    /** Off, done today, or failed within the hour — three reasons to do nothing. */
    private fun isDue(prefs: BackupsPrefs, now: Long): Boolean {
        if (!prefs.enabled) return false
        // There is already a copy from today, made either by the daily check or by the user.
        if (isSameLocalDay(prefs.lastRunAt, now)) return false
        // A clock moved backwards gives a negative interval: that is not "attempted recently",
        // it is an unusable timestamp, and the check goes ahead.
        return (now - prefs.lastAttemptAt) !in 0 until RETRY_INTERVAL_MS
    }

    /**
     * Makes a copy now, whatever the interval says, and returns its file name or
     * the reason it could not. Blocks — call it from a coroutine on IO or from a
     * background thread, never from the main one. Waits if the daily check is
     * already writing.
     *
     * This is what the "Create backup" button in an app's settings calls: a
     * manual copy is a copy, so it counts as the day's and takes part in the
     * same rotation. It ignores the on/off switch, which governs the automatic
     * copies and not a button the user just pressed.
     */
    fun runNow(context: Context, config: BackupsConfig): Result<String> {
        val app = context.applicationContext
        val prefs = BackupsPrefs(app)
        gate.acquire()
        return try {
            val now = System.currentTimeMillis()
            prefs.lastAttemptAt = now
            perform(app, config, prefs, now)
        } finally {
            gate.release()
        }
    }

    /** Whether the daily copy is on. The settings switch reads this. */
    fun isEnabled(context: Context): Boolean = BackupsPrefs(context).enabled

    /**
     * Turns the daily copy on or off. Turning it on does not make one on the
     * spot: the next start of the app or of its service does, like any other day.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        BackupsPrefs(context).enabled = enabled
    }

    /** When the last copy was written, or 0 if there has never been one. */
    fun lastRunAt(context: Context): Long = BackupsPrefs(context).lastRunAt

    /**
     * Writes one archive and then tidies the folder — in that order, always: a
     * backup that failed must not have cost the oldest copy that worked.
     */
    private fun perform(
        context: Context,
        config: BackupsConfig,
        prefs: BackupsPrefs,
        now: Long,
    ): Result<String> = runCatching {
        val name = config.filePrefix + TIMESTAMP.format(Date(now)) + config.extension
        val uri = BackupsStore.createPending(context, config, name)
        var written: String? = null
        try {
            val bytes = context.contentResolver.openOutputStream(uri)?.use { out ->
                val counting = CountingOutputStream(out)
                config.write(counting)
                counting.count
            } ?: error("Cannot open $name for writing")
            // An empty file is what a writer that failed quietly leaves behind, and
            // publishing it would push a real copy out of the pool.
            check(bytes > 0L) { "Backup writer produced no data" }
            written = BackupsStore.publish(context, uri, name)
        } finally {
            if (written == null) BackupsStore.discard(context, uri)
        }

        // Unreachable while the try above either assigns or throws; it is what makes the
        // published name non-null for the caller.
        val publishedName = checkNotNull(written) { "Backup was not published" }

        prefs.lastRunAt = now
        BackupsStore.prune(context, config, published = uri)
        publishedName
    }

    /** Same calendar day in the phone's own time zone — "once a day", not "every 24 hours". */
    private fun isSameLocalDay(first: Long, second: Long): Boolean {
        if (first <= 0L) return false
        val a = Calendar.getInstance().apply { timeInMillis = first }
        val b = Calendar.getInstance().apply { timeInMillis = second }
        return a.get(Calendar.ERA) == b.get(Calendar.ERA) &&
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * The timestamp in every file name, in the one shape [BackupsConfig.isPoolName]
     * recognises. Only ever touched inside [perform], which runs one at a time, so
     * its lack of thread-safety cannot bite.
     */
    private val TIMESTAMP = SimpleDateFormat(BackupsConfig.TIMESTAMP_PATTERN, Locale.US)

    /**
     * Counts what the app's writer actually produced. It delegates every write
     * rather than inheriting FilterOutputStream, whose block write goes one byte
     * at a time through the single-byte method.
     */
    private class CountingOutputStream(private val target: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            target.write(b)
            count++
        }

        override fun write(b: ByteArray) {
            target.write(b)
            count += b.size
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            target.write(b, off, len)
            count += len
        }

        override fun flush() = target.flush()

        override fun close() = target.close()
    }
}
