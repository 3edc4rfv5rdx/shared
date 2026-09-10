package dev.updater

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        val prefs = UpdaterPrefs(activity)
        if (!prefs.enabled) return
        // A clock moved backwards gives a negative interval: that is not "checked
        // recently", it is an unusable timestamp, and the check goes ahead — the
        // same rule ../backups applies to its own retry interval.
        val elapsed = System.currentTimeMillis() - prefs.lastCheckAt
        if (elapsed in 0 until config.checkIntervalHours * 3_600_000L) return
        check(activity, config, manual = false)
    }

    /**
     * The check behind a menu entry. It ignores the interval and a build the
     * user cancelled, and it reports what it found either way — a silent button is a
     * broken button.
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
     * A check that answers instead of showing anything: true when the release
     * folder holds a build newer than the installed one, false for everything
     * else — up to date, nothing for this device, a manifest for another app, an
     * unreachable server. For a caller that draws the answer itself, such as the
     * About screen's button.
     *
     * It remembers nothing. The interval and the declined build belong to the
     * check that actually offers an update; a look that only colours a button
     * must not spend the next start-up check's turn.
     *
     * [onResult] runs on the main thread, and not at all if the activity is gone
     * by the time the answer arrives.
     */
    fun peek(activity: Activity, config: UpdaterConfig, onResult: (Boolean) -> Unit) {
        val manifestUrl = config.manifest
        val packageName = activity.packageName
        val currentCode = currentVersionCode(activity)
        val abis = Build.SUPPORTED_ABIS.toList()

        Thread {
            val info = (fetch(manifestUrl, abis) as? ManifestResult.Ok)?.info
            val newer = info != null &&
                info.packageName == packageName &&
                info.versionCode > currentCode

            main.post {
                if (!activity.isFinishing && !activity.isDestroyed) onResult(newer)
            }
        }.start()
    }

    /** The manifest as this device reads it; null when the server said nothing usable. */
    private fun fetch(manifestUrl: String, abis: List<String>): ManifestResult? =
        UpdaterNet.fetchManifest(manifestUrl)?.let { UpdateInfo.parse(it, manifestUrl, abis) }

    private fun check(activity: Activity, config: UpdaterConfig, manual: Boolean) {
        // A second check while one is in flight would end in two dialogs over
        // each other, and both would be about the same version.
        if (!running.compareAndSet(false, true)) return

        val strings = UpdaterStrings.of(Locale.getDefault())
        val prefs = UpdaterPrefs(activity)
        val manifestUrl = config.manifest
        val currentCode = currentVersionCode(activity)

        // The device's own order of preference, so a 64-bit phone takes the
        // arm64 split and an armeabi-v7a box the v7a one out of one manifest.
        val abis = Build.SUPPORTED_ABIS.toList()

        Thread {
            // Whatever the worker dies of, the answer still reaches the main thread: a
            // throwable that escaped here would leave `running` set for the life of the
            // process, and every later check — the About button included — silent.
            val result = try {
                fetch(manifestUrl, abis)
            } catch (e: Throwable) {
                null
            }

            main.post {
                // `running` is held until the operation is over on screen: a message
                // is over as soon as it is up, the confirm dialog when it is closed
                // by anything but Update, and after Update the download carries it.
                val info = when (result) {
                    is ManifestResult.Ok -> result.info

                    // The server answered, so the check counts and the interval
                    // starts over; there is simply nothing here for this device.
                    is ManifestResult.NoBuildForDevice -> {
                        prefs.lastCheckAt = System.currentTimeMillis()
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

                // The check counts from here on: the server answered, and every road
                // below either has nothing to show or shows the offer. The one
                // exception is an offer nobody saw — see the confirm dialog below.
                prefs.lastCheckAt = System.currentTimeMillis()

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

                // An offer the user never saw must not spend the interval: an activity
                // gone before the dialog was up, or destroyed under it, gives the check
                // back its turn, so the next start-up looks again instead of six hours on.
                val shown = UpdaterDialog.confirm(
                    activity = activity,
                    strings = strings,
                    info = info,
                    onUpdate = { startDownload(activity, config, strings, info) },
                    onCancel = { prefs.declinedVersionCode = info.versionCode },
                    onClosed = { answered ->
                        if (!answered) prefs.lastCheckAt = 0L
                        running.set(false)
                    },
                )
                if (!shown) {
                    prefs.lastCheckAt = 0L
                    running.set(false)
                }
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
