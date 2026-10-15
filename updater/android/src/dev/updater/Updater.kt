package dev.updater

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The whole updater, as three calls: the silent check at start-up, the one
 * behind a button, and a look that answers its caller instead of the user.
 *
 * Nothing is downloaded before the user says so: a check fetches a few kilobytes
 * of manifest, and the APK is only fetched after the confirmation dialog comes
 * back positive.
 */
object Updater {

    /**
     * Held for the whole of one operation on screen — the fetch, the dialog it
     * ends in, and the download and install hand-off after Update — not for the
     * fetch alone. Released on every road out, and only on the main thread.
     * [peek] never takes it: a look that shows nothing has nothing to collide with.
     */
    private val running = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    /**
     * When the server last answered a start-up check, on the monotonic clock; null
     * before the first answer in this process.
     *
     * This is the whole of the rationing on asking: once per [LOOK_INTERVAL_MS]
     * while the process lives, and at once in a new process — a build installed
     * over this one is a new process. It is kept in memory and not in
     * preferences, because a stored clock let a look that found nothing silence
     * the hours in which the next build was published. It is not once per process
     * either: a player keeps its process for days, and a build published after its
     * one look was never offered.
     *
     * Set only once the server has actually answered, so a launch with no network
     * does not use up the look. Read and written on the main thread only.
     */
    private var lookedAt: Long? = null

    /** How long a start-up look stands before coming back to the app asks again. */
    private const val LOOK_INTERVAL_MS = 60 * 60 * 1000L

    // Repeats the start-up check whenever the launch activity comes back to the
    // screen. Registered by the first checkOnStart and kept for the process.
    private var startWatch: Application.ActivityLifecycleCallbacks? = null

    // The build the user agreed to install but could not yet, because the app
    // did not hold the install permission. Sending them to Settings takes them
    // out of the app, so the answer comes back as a resume rather than a result.
    private var pendingInfo: UpdateInfo? = null
    private var pendingConfig: UpdaterConfig? = null
    private var permissionWatch: Application.ActivityLifecycleCallbacks? = null

    /**
     * The silent check to call from the launch activity. It stays quiet unless
     * there is something newer to offer: no dialog for an up-to-date app, none
     * for an unreachable server, and none more often than the configured
     * interval or for a build the user cancelled. Nothing at all when the user
     * has turned it off — see [setEnabled].
     */
    fun checkOnStart(activity: Activity, config: UpdaterConfig) {
        // Nothing is read here, not even the flag: SharedPreferences is a file, and the
        // start-up path of an activity is no place to open one. The decision is the
        // worker's — the same rule ../backups applies to its own start-up call.
        watchStarts(activity, config)
        checkIfDue(activity, config)
    }

    /**
     * The start-up check, unless the server answered one within the interval.
     * [lookedAt] is in memory and costs nothing to read: an activity recreated by
     * a theme or a rotation, or brought back a minute later, does not ask again.
     */
    private fun checkIfDue(activity: Activity, config: UpdaterConfig) {
        val last = lookedAt
        if (last != null && SystemClock.elapsedRealtime() - last < LOOK_INTERVAL_MS) return
        check(activity, config, manual = false)
    }

    /**
     * Calls [checkIfDue] each time an activity of the caller's class starts.
     *
     * An app brought back from the background gets its activity back as it was,
     * with no onCreate for the call to sit in, so the call alone would never run
     * again while the process lives. Watching here keeps every app's call where it
     * is. The start right after the first call finds the check already running
     * and does nothing.
     */
    private fun watchStarts(activity: Activity, config: UpdaterConfig) {
        if (startWatch != null) return
        val launcher = activity.javaClass

        val watch = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (activity.javaClass == launcher) checkIfDue(activity, config)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        startWatch = watch
        activity.application.registerActivityLifecycleCallbacks(watch)
    }

    /**
     * The check behind a menu entry. It ignores the offer interval, the switch
     * and a build the user cancelled, and it reports what it found either way —
     * a silent button is a broken button. It does not count as the start-up
     * check's look either: a button pressed here must not put the next silent
     * check off.
     */
    fun checkNow(activity: Activity, config: UpdaterConfig) {
        check(activity, config, manual = true)
    }

    /**
     * Whether the start-up check is on. The settings switch reads this.
     *
     * It governs [checkOnStart] alone: [checkNow] is a button the user has just
     * pressed and [peek] is a screen asking on their behalf, and neither is the
     * unasked-for interruption this switch exists to stop.
     */
    fun isEnabled(context: Context): Boolean = UpdaterPrefs(context).enabled

    /** Turns the start-up check on or off. */
    fun setEnabled(context: Context, enabled: Boolean) {
        UpdaterPrefs(context).enabled = enabled
    }

    /**
     * A check that answers instead of showing anything: the build on the server
     * when it is newer than the installed one, null for everything else — up to
     * date, nothing for this device, a manifest for another app, an unreachable
     * server. For a caller that draws the answer itself, such as the About
     * screen's button.
     *
     * It hands back the whole build rather than a yes, so the caller can name
     * the version on its button and then pass it to [install] instead of sending
     * the user through a second look at the same manifest.
     *
     * It remembers nothing. The interval and the declined build belong to the
     * check that actually offers an update; a look that only colours a button
     * must not spend the next start-up check's turn.
     *
     * [onResult] runs on the main thread, and not at all if the activity is gone
     * by the time the answer arrives.
     */
    fun peek(activity: Activity, config: UpdaterConfig, onResult: (UpdateInfo?) -> Unit) {
        val manifestUrl = config.manifest
        val packageName = activity.packageName
        val currentCode = currentVersionCode(activity)
        val abis = Build.SUPPORTED_ABIS.toList()

        Thread {
            val found = try {
                (fetch(manifestUrl, abis) as? ManifestResult.Ok)?.info
            } catch (e: Throwable) {
                null
            }
            val newer = found?.takeIf {
                it.packageName == packageName && it.versionCode > currentCode
            }

            main.post {
                if (!activity.isFinishing && !activity.isDestroyed) onResult(newer)
            }
        }.start()
    }

    /**
     * Installs a build [peek] has already found, with no question in between:
     * the caller's own button carried the offer and its version, and asking the
     * same thing again in a dialog is one tap for nothing.
     *
     * Everything the confirm dialog guards is still here — the permission ask,
     * the progress dialog, the digest check — and the download takes [running]
     * exactly as a check that ended in Update would. A second operation already
     * on screen wins and this one does nothing.
     */
    fun install(activity: Activity, config: UpdaterConfig, info: UpdateInfo) {
        if (!running.compareAndSet(false, true)) return
        startDownload(activity, config, UpdaterStrings.of(Locale.getDefault()), info)
    }

    /** The manifest as this device reads it; null when the server said nothing usable. */
    private fun fetch(manifestUrl: String, abis: List<String>): ManifestResult? =
        UpdaterNet.fetchManifest(manifestUrl)?.let { UpdateInfo.parse(it, manifestUrl, abis) }

    private fun check(activity: Activity, config: UpdaterConfig, manual: Boolean) {
        // A second check while one is in flight would end in two dialogs over
        // each other, and both would be about the same version.
        if (!running.compareAndSet(false, true)) return

        val strings = UpdaterStrings.of(Locale.getDefault())
        val manifestUrl = config.manifest

        // The device's own order of preference, so a 64-bit phone takes the
        // arm64 split and an armeabi-v7a box the v7a one out of one manifest.
        val abis = Build.SUPPORTED_ABIS.toList()

        Thread {
            // Whatever the worker dies of, the answer still reaches the main thread: a
            // throwable that escaped here would leave `running` set for the life of the
            // process, and every later check — the About button included — silent.
            val prefs = UpdaterPrefs(activity)

            // The start-up check's one reason to do nothing before it has asked
            // anything, decided off the main thread: the user switched it off. A
            // manual check is the user asking, so the switch does not apply to it.
            if (!manual && !prefs.enabled) {
                main.post { running.set(false) }
                return@Thread
            }

            val currentCode = currentVersionCode(activity)
            val result = try {
                fetch(manifestUrl, abis)
            } catch (e: Throwable) {
                null
            }

            main.post {
                // Written the moment the user has dealt with the offer, whichever
                // way. It is what the start-up check throttles on, so an offer
                // nobody saw — an activity gone before the dialog was up, or
                // destroyed under it — writes nothing and is made again next launch.
                fun markOffered(versionCode: Long) {
                    prefs.lastOfferedVersionCode = versionCode
                    prefs.lastOfferedAt = System.currentTimeMillis()
                }

                // `running` is held until the operation is over on screen: a message
                // is over as soon as it is up, the confirm dialog when it is closed
                // by anything but Update, and after Update the download carries it.
                val info = when (result) {
                    is ManifestResult.Ok -> result.info

                    // The server answered, so this look counts; there is simply
                    // nothing here for this device.
                    is ManifestResult.NoBuildForDevice -> {
                        if (!manual) lookedAt = SystemClock.elapsedRealtime()
                        if (manual) UpdaterDialog.message(activity, strings.errorNoBuild)
                        running.set(false)
                        return@post
                    }

                    null, is ManifestResult.Invalid -> {
                        if (manual) UpdaterDialog.message(activity, strings.errorNetwork)
                        running.set(false)
                        return@post
                    }
                }

                // The server answered, so this look counts. Whether an offer is made
                // below is a separate question with its own memory.
                if (!manual) lookedAt = SystemClock.elapsedRealtime()

                // A manifest for another app means the config points at the wrong
                // folder. Installing it would put a second app on the phone rather
                // than update this one, so it is dropped instead. This is also why
                // a debug build stays quiet: applicationIdSuffix makes its package
                // ".debug", which no published manifest names.
                if (info.packageName != activity.packageName) {
                    if (manual) UpdaterDialog.message(activity, strings.errorNetwork)
                    running.set(false)
                    return@post
                }

                if (info.versionCode <= currentCode) {
                    if (manual) UpdaterDialog.message(activity, strings.upToDate)
                    running.set(false)
                    return@post
                }

                if (!manual && info.versionCode == prefs.declinedVersionCode) {
                    running.set(false)
                    return@post
                }

                // The same build, put off not long ago. Offering it again every time
                // the app is opened is the nagging the interval exists to stop — and
                // all it stops: this is the one road that waits on a clock, and only
                // for a build this user has already answered about. A clock moved
                // backwards gives a negative age, which is not "recently shown" but an
                // unusable timestamp, and the offer goes ahead — the same rule
                // ../backups applies to its own retry interval.
                if (!manual && info.versionCode == prefs.lastOfferedVersionCode) {
                    val age = System.currentTimeMillis() - prefs.lastOfferedAt
                    if (age in 0 until config.offerIntervalHours * 3_600_000L) {
                        running.set(false)
                        return@post
                    }
                }

                val shown = UpdaterDialog.confirm(
                    activity = activity,
                    strings = strings,
                    info = info,
                    onUpdate = {
                        markOffered(info.versionCode)
                        startDownload(activity, config, strings, info)
                    },
                    onCancel = { prefs.declinedVersionCode = info.versionCode },
                    onClosed = { answered ->
                        if (answered) markOffered(info.versionCode)
                        running.set(false)
                    },
                )
                if (!shown) running.set(false)
            }
        }.start()
    }

    /**
     * The download, with [running] already held by the caller — the confirm dialog
     * hands it over on Update, the permission watch takes it before calling. It is
     * released here on every road out: the permission dialog closing, a cancelled
     * or failed transfer, the file handed to the installer.
     */
    private fun startDownload(
        activity: Activity,
        config: UpdaterConfig,
        strings: UpdaterStrings,
        info: UpdateInfo,
    ) {
        // Asked before the download rather than after it, so a missing permission
        // costs a tap instead of a finished transfer that goes nowhere.
        if (!UpdaterInstall.canInstall(activity)) {
            // The install is remembered only once the user is actually on their way to
            // Settings. Armed any earlier, a Cancel here would leave it waiting for the
            // next resume of any screen, and a permission granted by other means later
            // would start a download nobody had just asked for.
            val shown = UpdaterDialog.installSources(
                activity = activity,
                strings = strings,
                onOpenSettings = {
                    if (UpdaterInstall.openInstallSourcesSettings(activity)) {
                        pendingInfo = info
                        pendingConfig = config
                        watchForPermission(activity.application)
                    }
                },
                onClosed = { running.set(false) },
            )
            if (!shown) running.set(false)
            return
        }

        val progress = UpdaterDialog.Progress(activity, strings)
        if (!progress.show()) {
            running.set(false)
            return
        }

        val dir = File(activity.cacheDir, "updater")
        dir.mkdirs()
        // Whatever a previous run left here is either the build now installed or
        // one abandoned half-way; neither is worth the space. Nothing here is in
        // flight: `running` has been held since before the confirm dialog.
        dir.listFiles()?.forEach { it.delete() }

        // Written under a name the installer is never given; it becomes the .apk
        // only once its digest has been checked, so a file called .apk in this
        // folder is a file that was verified, and a killed download leaves a .part.
        val target = File(dir, "${config.appKey}-${info.versionCode}.apk")
        val part = File(dir, "${target.name}.part")

        Thread {
            var lastPercent = -1
            // Same rule as the check: the progress dialog cannot be cancelled by the user,
            // so a worker that died without posting would leave it up for good.
            val actual = try {
                UpdaterNet.download(
                    url = info.url,
                    target = part,
                    expectedSize = info.size,
                    isCancelled = { progress.cancelled },
                    onProgress = { downloaded, total ->
                        if (total > 0L) {
                            val percent = ((downloaded * 100L) / total).toInt()
                            // One post per percent, not one per 64 KB block.
                            if (percent != lastPercent) {
                                lastPercent = percent
                                main.post { progress.update(downloaded, total) }
                            }
                        }
                    },
                )
            } catch (e: Throwable) {
                null
            }

            main.post {
                progress.dismiss()
                finishDownload(activity, strings, info, part, target, actual, progress.cancelled)
                running.set(false)
            }
        }.start()
    }

    /** What happens to the transferred bytes: thrown away, or renamed and installed. */
    private fun finishDownload(
        activity: Activity,
        strings: UpdaterStrings,
        info: UpdateInfo,
        part: File,
        target: File,
        actual: String?,
        cancelled: Boolean,
    ) {
        if (cancelled) {
            part.delete()
            return
        }

        if (actual == null) {
            part.delete()
            UpdaterDialog.message(activity, strings.errorNetwork)
            return
        }

        // The APK's own signature is what actually protects the install —
        // Android refuses a package signed with another key. This checks
        // the file arrived whole, which a truncated transfer would not.
        if (!actual.equals(info.sha256, ignoreCase = true)) {
            part.delete()
            UpdaterDialog.message(activity, strings.errorCorrupt)
            return
        }

        if (!part.renameTo(target)) {
            part.delete()
            UpdaterDialog.message(activity, strings.errorNetwork)
            return
        }

        UpdaterInstall.install(activity, target)
    }

    /**
     * Picks the install back up when the user returns from the Settings screen.
     *
     * Nothing here can use an activity result: the dialog that sends them there
     * is not a result contract, and the updater is not an Activity to receive
     * one. The next resume of any activity of this app is the signal, and the
     * watch is dropped on that first resume however it turns out — a user who
     * refused the permission is not followed around the app.
     */
    private fun watchForPermission(app: Application) {
        if (permissionWatch != null) return

        val watch = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                stopWatchingForPermission(app)

                val info = pendingInfo ?: return
                val config = pendingConfig ?: return
                pendingInfo = null
                pendingConfig = null

                // A new operation, so it takes `running` like a check would; one already
                // in flight — a start-up check of the screen just resumed — wins, and
                // that check offers the same build again.
                if (UpdaterInstall.canInstall(activity) && running.compareAndSet(false, true)) {
                    startDownload(activity, config, UpdaterStrings.of(Locale.getDefault()), info)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }

        permissionWatch = watch
        app.registerActivityLifecycleCallbacks(watch)
    }

    private fun stopWatchingForPermission(app: Application) {
        permissionWatch?.let { app.unregisterActivityLifecycleCallbacks(it) }
        permissionWatch = null
    }

    private fun currentVersionCode(context: Context): Long {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }
}
