package dev.about

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
            // The start-up check keeps six hours between two looks at the server,
            // which is right for a phone and useless for a build published a
            // minute ago. This one ignores the interval and answers either way.
            onUpdate = if (config.offerUpdate) {
                { Updater.checkNow(activity, config.updater) }
            } else {
                null
            },
            // Asked as the card comes up, so the button can say whether there is
            // anything to update to instead of promising it. The tap still goes
            // through checkNow: this look decides nothing and installs nothing.
            onProbe = if (config.offerUpdate) {
                { answer -> Updater.peek(activity, config.updater, answer) }
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
