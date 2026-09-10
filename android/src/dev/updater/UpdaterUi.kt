package dev.updater

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The pieces every dialog in these projects is made of — the updater's own and
 * ../about's alike — in the colours [UpdaterColors] keeps for both.
 *
 * One card, one title, one pill, one way of putting a dialog on screen, written
 * here once: the two modules draw the same card, and a card that changes shape
 * in one of them and not the other reads as two different apps talking.
 *
 * Nothing here is Compose or Flutter: one kit serves both stacks, so its look
 * is its own in every host instead of half-belonging to whichever toolkit the
 * host happens to use. Black on white, in every app and either theme.
 */
internal class UpdaterUi(val activity: Activity) {
    private val density = activity.resources.displayMetrics.density

    fun dp(value: Int): Int = (value * density).toInt()

    /** The dialog body: a column on a white card of its own. */
    fun content(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(24), dp(24), dp(16))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(UpdaterColors.card)
        }
    }

    fun title(text: String): TextView = TextView(activity).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setTextColor(UpdaterColors.text)
        setPadding(0, 0, 0, dp(16))
    }

    fun body(text: String): TextView = TextView(activity).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
        setTextColor(UpdaterColors.text)
    }

    /**
     * A pill: fully rounded, because the radius is larger than the button can
     * ever be tall, so the ends stay semicircular whatever the text does to
     * the height.
     */
    fun pill(label: String, inverted: Boolean = false, onClick: () -> Unit): TextView =
        TextView(activity).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            isClickable = true
            style(this, label, inverted)
            setOnClickListener { onClick() }
        }

    /**
     * What a pill says and how it is filled, in one place: a pill can be drawn
     * once before an answer is known and again with it — ../about's update
     * button is — and the two have to agree on everything but the fill.
     *
     * The one the dialog is really asking for is filled; the rest are the same
     * violet drained to a tint, and both carry the outline.
     */
    fun style(pill: TextView, label: String, inverted: Boolean) {
        pill.text = label
        pill.setTextColor(if (inverted) UpdaterColors.onAccent else UpdaterColors.onAccentSoft)
        pill.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(100).toFloat()
            setColor(if (inverted) UpdaterColors.accent else UpdaterColors.accentSoft)
            setStroke(dp(2), UpdaterColors.pillStroke)
        }
    }

    /** The pills stacked, each across the full width. */
    fun buttons(vararg pills: View): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(24), 0, 0)
        pills.forEachIndexed { index, pill ->
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            if (index > 0) params.topMargin = dp(10)
            addView(pill, params)
        }
    }

    companion object {
        /** A dialog can only be shown on an activity that is still there to hold it. */
        fun alive(activity: Activity): Boolean = !activity.isFinishing && !activity.isDestroyed

        /**
         * Shows a dialog whose content already carries its own card: the window's
         * own background would otherwise sit behind it as a second, differently
         * coloured panel with square corners showing past the rounded ones.
         *
         * False when the activity is no longer there to hold it and nothing was
         * shown — the caller waiting on the dialog's answer has to know that no
         * answer is coming.
         *
         * [onDismiss] is the one dismiss listener the dialog gets, and it fires
         * however the dialog goes: a pill, a tap outside, Back — or the activity
         * being destroyed under it, which the framework does without a word to
         * the dialog. The activity is watched for that and the dialog dismissed
         * by hand, so an operation waiting on the answer is never left waiting
         * for good. The flag says which it was: true when the user closed it,
         * false when the activity went and nobody saw the dialog to its end.
         */
        fun present(
            activity: Activity,
            dialog: AlertDialog,
            onDismiss: ((answered: Boolean) -> Unit)? = null,
        ): Boolean {
            if (!alive(activity)) return false

            val app = activity.application
            var lost = false
            val watch = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityDestroyed(destroyed: Activity) {
                    // Before the framework closes the windows, so this is an ordinary dismiss.
                    if (destroyed === activity) {
                        lost = true
                        dialog.dismiss()
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            }
            app.registerActivityLifecycleCallbacks(watch)
            dialog.setOnDismissListener {
                app.unregisterActivityLifecycleCallbacks(watch)
                onDismiss?.invoke(!lost)
            }

            val window = dialog.window
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Centred on purpose: a host on the platform's own DeviceDefault theme
            // gets whatever the vendor does with dialogs, and One UI slides them to
            // the bottom edge. One dialog in eight apps means one position too.
            window?.setGravity(Gravity.CENTER)
            // Force Dark would repaint this hierarchy on its own in a dark host — the
            // white card turned dark and the black text light, which is exactly the
            // guessing this dialog was made not to do.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window?.decorView?.isForceDarkAllowed = false
            }
            dialog.show()
            return true
        }
    }
}
