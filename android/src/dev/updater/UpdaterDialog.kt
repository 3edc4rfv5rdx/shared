package dev.updater

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Every screen the updater puts up, built in code.
 *
 * The dialogs carry their own content and their own buttons rather than the
 * platform's: one updater serves Compose apps and Flutter apps alike, so its
 * look is its own in all of them instead of half-belonging to whichever toolkit
 * the host happens to use. Black on white, in every app and either theme.
 */
internal object UpdaterDialog {

    /** How tall the download bar is drawn, in dp. */
    private const val BAR_HEIGHT_DP = 14

    /** A dialog can only be shown on an activity that is still there to hold it. */
    private fun alive(activity: Activity): Boolean = !activity.isFinishing && !activity.isDestroyed

    /**
     * Shows a dialog whose content already carries its own card: the window's own
     * background would otherwise sit behind it as a second, differently coloured
     * panel with square corners showing past the rounded ones.
     *
     * False when the activity is no longer there to hold it and nothing was
     * shown — the caller waiting on the dialog's answer has to know that no
     * answer is coming.
     *
     * [onDismiss] is the one dismiss listener the dialog gets, and it fires
     * however the dialog goes: a pill, a tap outside, Back — or the activity
     * being destroyed under it, which the framework does without a word to the
     * dialog. The activity is watched for that and the dialog dismissed by hand,
     * so an operation waiting on the answer is never left waiting for good.
     */
    private fun present(activity: Activity, dialog: AlertDialog, onDismiss: (() -> Unit)? = null): Boolean {
        if (!alive(activity)) return false

        val app = activity.application
        val watch = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityDestroyed(destroyed: Activity) {
                // Before the framework closes the windows, so this is an ordinary dismiss.
                if (destroyed === activity) dialog.dismiss()
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
            onDismiss?.invoke()
        }

        val window = dialog.window
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        // Centred on purpose: a host on the platform's own DeviceDefault theme
        // gets whatever the vendor does with dialogs, and One UI slides them to
        // the bottom edge. One updater in eight apps means one position too.
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

    /**
     * The pieces every dialog here is made of, in the colours [UpdaterColors]
     * keeps for both this module and ../about.
     */
    private class Ui(val activity: Activity) {
        private val density = activity.resources.displayMetrics.density

        private val cardFill = UpdaterColors.card
        private val trackFill = UpdaterColors.track
        private val textColor = UpdaterColors.text

        fun dp(value: Int): Int = (value * density).toInt()

        /** The dialog body: a column on a white card of its own. */
        fun content(): LinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(16))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(cardFill)
            }
        }

        fun title(text: String): TextView = TextView(activity).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(textColor)
            setPadding(0, 0, 0, dp(16))
        }

        fun body(text: String): TextView = TextView(activity).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            setTextColor(textColor)
        }

        /**
         * A pill: fully rounded, because the radius is larger than the button can
         * ever be tall, so the ends stay semicircular whatever the text does to
         * the height.
         */
        fun pill(label: String, inverted: Boolean = false, onClick: () -> Unit): TextView =
            TextView(activity).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                // The one the dialog is really asking for is filled; the rest are
                // the same violet drained to a tint, and both carry the outline.
                setTextColor(if (inverted) UpdaterColors.onAccent else UpdaterColors.onAccentSoft)
                setPadding(dp(14), dp(13), dp(14), dp(13))
                isClickable = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(100).toFloat()
                    setColor(if (inverted) UpdaterColors.accent else UpdaterColors.accentSoft)
                    setStroke(dp(2), UpdaterColors.pillStroke)
                }
                setOnClickListener { onClick() }
            }

        /**
         * A progress bar drawn like everything else here: a tinted track with an
         * accent fill, both rounded, and as tall as it is told to be. The platform
         * bar is a few pixels of the host's accent colour and ignores the height
         * it is given.
         */
        fun progressBar(): ProgressBar {
            val radius = dp(BAR_HEIGHT_DP / 2).toFloat()
            val track = GradientDrawable().apply {
                cornerRadius = radius
                setColor(trackFill)
            }
            val fill = GradientDrawable().apply {
                cornerRadius = radius
                setColor(UpdaterColors.accent)
            }
            val layers = LayerDrawable(
                arrayOf(track, ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL))
            )
            layers.setId(0, android.R.id.background)
            layers.setId(1, android.R.id.progress)

            return ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                progressDrawable = layers
                max = 100
                progress = 0
                // Never indeterminate: that mode draws a different drawable, the
                // platform's, and the bar would change shape mid-download on the
                // one response that omits a length.
                isIndeterminate = false
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
    }

    /**
     * The one question the updater asks: this build exists, install it?
     *
     * Three answers, because "not now" and "not this one" are different things.
     * Update downloads; Cancel puts this exact build aside and stops offering it,
     * while a newer one still gets through; Later remembers nothing, so the next
     * check after the interval puts the same version up again.
     *
     * [onClosed] runs when the dialog goes away by any road but Update — Later,
     * Cancel, a tap outside — and is how the caller learns the operation is over;
     * after Update it is [onUpdate] that carries the operation on. Returns false,
     * calling nothing, when there was no activity to show it on.
     */
    fun confirm(
        activity: Activity,
        strings: UpdaterStrings,
        info: UpdateInfo,
        onUpdate: () -> Unit,
        onCancel: () -> Unit,
        onClosed: () -> Unit,
    ): Boolean {
        val ui = Ui(activity)
        val content = ui.content()
        content.addView(ui.title(strings.title))
        content.addView(
            ui.body(
                "${strings.fieldVersion}: ${info.versionName}\n" +
                    "${strings.fieldBuild}: ${info.versionCode}"
            )
        )

        val dialog = AlertDialog.Builder(activity).setView(content).create()
        var updating = false
        content.addView(
            ui.buttons(
                ui.pill(strings.update, inverted = true) { updating = true; dialog.dismiss(); onUpdate() },
                ui.pill(strings.cancel) { dialog.dismiss(); onCancel() },
                ui.pill(strings.later) { dialog.dismiss() },
            )
        )
        // Dismissal is delivered later on the main looper, not inside dismiss(), so
        // this is the one place that sees every way out of the dialog at once.
        return present(activity, dialog) { if (!updating) onClosed() }
    }

    fun message(activity: Activity, text: String) {
        val ui = Ui(activity)
        val content = ui.content()
        content.addView(ui.body(text))

        val dialog = AlertDialog.Builder(activity).setView(content).create()
        content.addView(ui.buttons(ui.pill(activity.getString(android.R.string.ok)) { dialog.dismiss() }))
        present(activity, dialog)
    }

    /**
     * The permission ask. [onOpenSettings] runs on the Settings pill and is where the
     * caller both sends the user there and decides what to remember; Cancel remembers
     * nothing, so nothing is armed for a user who declined. [onClosed] runs once the
     * dialog is gone whichever pill it was — the operation on screen is over either way,
     * and a resume from Settings starts a new one. Returns false, calling nothing, when
     * there was no activity to show it on.
     */
    fun installSources(
        activity: Activity,
        strings: UpdaterStrings,
        onOpenSettings: () -> Unit,
        onClosed: () -> Unit,
    ): Boolean {
        val ui = Ui(activity)
        val content = ui.content()
        content.addView(ui.title(strings.installSourcesTitle))
        content.addView(ui.body(strings.installSourcesBody))

        val dialog = AlertDialog.Builder(activity).setView(content).create()
        content.addView(
            ui.buttons(
                ui.pill(strings.openSettings) { dialog.dismiss(); onOpenSettings() },
                ui.pill(strings.cancel) { dialog.dismiss() },
            )
        )
        return present(activity, dialog, onClosed)
    }

    /**
     * The download's own dialog. It owns a cancel flag rather than reporting a
     * dismissal, so the worker thread has one thing to read and the user can
     * stop a download that is going nowhere.
     */
    class Progress(
        private val activity: Activity,
        private val strings: UpdaterStrings,
    ) {
        @Volatile
        var cancelled: Boolean = false
            private set

        private val label: TextView
        private val bar: ProgressBar
        private val dialog: AlertDialog

        init {
            val ui = Ui(activity)
            label = ui.body(strings.downloading)
            bar = ui.progressBar()

            val content = ui.content()
            content.addView(label)
            content.addView(
                bar,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ui.dp(BAR_HEIGHT_DP),
                ).apply { topMargin = ui.dp(16) },
            )

            dialog = AlertDialog.Builder(activity)
                .setView(content)
                .setCancelable(false)
                .create()

            content.addView(ui.buttons(ui.pill(strings.cancel) { cancelled = true }))
        }

        /** False when there is no activity to show it on, and no download should start. */
        fun show(): Boolean = present(activity, dialog)

        /** Called from the main thread as bytes arrive. */
        fun update(downloaded: Long, total: Long) {
            if (total > 0L) {
                val percent = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                bar.progress = percent
                label.text = "${strings.downloading} $percent%"
            }
        }

        fun dismiss() {
            try {
                dialog.dismiss()
            } catch (e: Exception) {
                // The activity went away under the dialog; nothing left to close.
            }
        }
    }
}
