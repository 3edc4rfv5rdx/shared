package dev.updater

import android.app.Activity
import dev.about.AboutOffer
import dev.about.AboutUpdates

/**
 * What About's update button asks of the updater: pass one as
 * AboutConfig.updates. It lives on this side because the updater builds on
 * ../about and not the other way round — a build that leaves the updater out
 * leaves this out with it, and About simply has no update button.
 */
class AboutUpdater(private val config: UpdaterConfig) : AboutUpdates {

    override fun peek(activity: Activity, answer: (AboutOffer?) -> Unit) {
        Updater.peek(activity, config) { info ->
            answer(
                info?.let { found ->
                    // The button already said "Update 0.8.263" and the user pressed
                    // it. A confirm dialog here would ask the same question twice
                    // and fetch the same manifest again.
                    AboutOffer(found.versionName) { host -> Updater.install(host, config, found) }
                }
            )
        }
    }

    // The start-up check keeps six hours between two looks at the server, which is
    // right for a phone and useless for a build published a minute ago; this one
    // ignores the interval and reports what it found either way.
    override fun checkNow(activity: Activity) = Updater.checkNow(activity, config)
}
