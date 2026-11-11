package dev.about

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.util.Locale

/**
 * The About screen, as one call.
 *
 * What is on it comes from two places and none of it is written twice: the
 * name and the version off the installed package, and the rest — the build
 * date first, the one fact no API knows — out of [AboutConfig].
 */
object About {

    /**
     * The app's accent as 0xAARRGGBB, for the buttons, the links and ../updater's
     * progress bar; null leaves them blue-grey.
     *
     * One value for the process rather than a field of [AboutConfig]: the
     * updater's start-up dialog comes up before anyone has opened About, and it
     * has to be in the same colour. The app sets it when it starts and again
     * whenever its accent changes — handed over, because this code cannot see a
     * Compose or Flutter theme. Read when a dialog is built, never cached.
     */
    @Volatile
    var accent: Int? = null

    /**
     * Puts the dialog up. Safe to call from anywhere a screen is on: an activity
     * on its way out is answered with nothing rather than a crash.
     */
    fun show(activity: Activity, config: AboutConfig) {
        val strings = AboutStrings.of(Locale.getDefault())
        // The build the probe found for this card, if it has answered by the time
        // the user taps. One card's worth of state, gone with the call.
        var offered: AboutOffer? = null
        val updates = config.updates

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
            repoUrl = config.repoUrl,
            colors = AboutColors.current(),
            email = config.email,
            onOpen = { url -> open(activity, Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            onMail = { address ->
                open(activity, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
            },
            onUpdate = updates?.let { source ->
                {
                    // Whatever the probe below found, held from the moment it answers
                    // until the button is tapped. Main thread on both sides: the probe
                    // posts its answer there and the tap arrives there, so there is
                    // nothing to guard. Nothing found, or no answer yet: the button
                    // still reads as a check, so it does one.
                    val found = offered
                    if (found != null) found.install(activity) else source.checkNow(activity)
                }
            },
            // Asked as the card comes up, so the button can name the version there
            // is to update to instead of promising one. This look decides nothing
            // and installs nothing; it only says what the button should read.
            onProbe = updates?.let { source ->
                { answer: (String?) -> Unit ->
                    source.peek(activity) { offer ->
                        offered = offer
                        answer(offer?.versionName)
                    }
                }
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
