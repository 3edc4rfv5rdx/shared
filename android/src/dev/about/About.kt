package dev.about

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dev.updater.UpdateInfo
import dev.updater.Updater
import java.util.Locale

/**
 * The About screen, as one call.
 *
 * What is on it comes from three places and none of them is written twice: the
 * name and the version off the installed package, the repository address out of
 * the app's [dev.updater.UpdaterConfig], and the build date — the one fact no
 * API knows — out of [AboutConfig].
 */
object About {

    /**
     * Puts the dialog up. Safe to call from anywhere a screen is on: an activity
     * on its way out is answered with nothing rather than a crash.
     */
    fun show(activity: Activity, config: AboutConfig) {
        val strings = AboutStrings.of(Locale.getDefault())
        // The build the probe found for this card, if it has answered by the time
        // the user taps. One card's worth of state, gone with the call.
        var offered: UpdateInfo? = null

        val facts = mutableListOf(strings.version to versionName(activity))
        // A tree that has never been built through the scripts carries no date,
        // and an empty line under a label reads as something gone missing.
        if (config.buildDate.isNotEmpty()) facts.add(strings.buildDate to config.buildDate)
        facts.addAll(config.extra)

        AboutDialog.show(
            activity = activity,
            strings = strings,
            appName = appName(activity),
            description = config.description,
            facts = facts,
            repoUrl = config.updater.repoUrl,
            email = config.email,
            onOpen = { url -> open(activity, Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            onMail = { address ->
                open(activity, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
            },
            // Whatever the probe below found, held from the moment it answers until
            // the button is tapped. Main thread on both sides: the probe posts its
            // answer there and the tap arrives there, so there is nothing to guard.
            onUpdate = if (config.offerUpdate) {
                {
                    val found = offered
                    if (found != null) {
                        // The button already said "Update 0.8.263" and the user
                        // pressed it. A confirm dialog here would ask the same
                        // question twice and fetch the same manifest again.
                        Updater.install(activity, config.updater, found)
                    } else {
                        // Nothing found, or the probe has not answered yet: the
                        // button still reads as a check, so it does one. The
                        // start-up check keeps six hours between two looks at the
                        // server, which is right for a phone and useless for a
                        // build published a minute ago; this one ignores the
                        // interval and reports what it found either way.
                        Updater.checkNow(activity, config.updater)
                    }
                }
            } else {
                null
            },
            // Asked as the card comes up, so the button can name the version there
            // is to update to instead of promising one. This look decides nothing
            // and installs nothing; it only says what the button should read.
            onProbe = if (config.offerUpdate) {
                { answer ->
                    Updater.peek(activity, config.updater) { info ->
                        offered = info
                        answer(info?.versionName)
                    }
                }
            } else {
                null
            },
        )
    }

    /** The launcher label, so the dialog is headed the way the app is on screen. */
    private fun appName(activity: Activity): String =
        activity.applicationInfo.loadLabel(activity.packageManager).toString()

    /**
     * The version as installed, rather than as written anywhere in the sources:
     * this is the number the user would have to quote, and the only one that is
     * certainly what is running.
     */
    private fun versionName(activity: Activity): String = try {
        @Suppress("DEPRECATION")
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty()
    } catch (e: PackageManager.NameNotFoundException) {
        ""
    }

    /**
     * A phone with no browser, or none that takes mailto:, is a phone where the
     * link does nothing — not one where the app stops.
     */
    private fun open(activity: Activity, intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Nothing to open it with; the address is on screen to be read anyway.
        }
    }
}
