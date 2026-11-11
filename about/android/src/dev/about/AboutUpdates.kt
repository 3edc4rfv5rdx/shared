package dev.about

import android.app.Activity

/**
 * What the About dialog needs from an updater, and all it needs: a silent look
 * that can name a newer build, and a check the user asked for.
 *
 * An interface rather than calls into ../updater, so this module builds without
 * it: the updater depends on About, never the other way round. A store build
 * that carries no updater passes nothing to [AboutConfig.updates] and the
 * dialog has no update button; a build that carries it passes the updater's
 * own AboutUpdater.
 */
interface AboutUpdates {

    /**
     * Asked as the dialog opens. Answers on the main thread with the build there
     * is to update to, or null for none; it may also never answer, and the
     * button then stays a check.
     */
    fun peek(activity: Activity, answer: (AboutOffer?) -> Unit)

    /** The button pressed with nothing found yet: look now and say what was found. */
    fun checkNow(activity: Activity)
}

/**
 * A newer build a [AboutUpdates.peek] found: the version the button names, and
 * how to put it on — without asking again, since the button already did.
 */
class AboutOffer(val versionName: String, val install: (Activity) -> Unit)
