package dev.updater

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Every screen the updater puts up, built in code out of [UpdaterUi] — the
 * card, the pills and the way on screen are that kit's, shared with ../about;
 * what is here is which dialogs exist and what each one asks.
 */
internal object UpdaterDialog {

    /** How tall the download bar is drawn, in dp. */
    private const val BAR_HEIGHT_DP = 14

    /**
     * A progress bar drawn like everything else here: a tinted track with an
     * accent fill, both rounded, and as tall as it is told to be. The platform
     * bar is a few pixels of the host's accent colour and ignores the height
     * it is given. The updater's own: nothing in ../about downloads.
     */
    private fun progressBar(ui: UpdaterUi): ProgressBar {
        val radius = ui.dp(BAR_HEIGHT_DP / 2).toFloat()
        val track = GradientDrawable().apply {
            cornerRadius = radius
            setColor(UpdaterColors.track)
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

        return ProgressBar(ui.activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressDrawable = layers
            max = 100
            progress = 0
            // Never indeterminate: that mode draws a different drawable, the
            // platform's, and the bar would change shape mid-download on the
            // one response that omits a length.
            isIndeterminate = false
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
     * after Update it is [onUpdate] that carries the operation on. Its flag is
     * false when the activity was destroyed under the dialog: nobody answered,
     * and the caller decides whether that check counted. Returns false, calling
     * nothing, when there was no activity to show it on.
     */
    fun confirm(
        activity: Activity,
        strings: UpdaterStrings,
        info: UpdateInfo,
        onUpdate: () -> Unit,
        onCancel: () -> Unit,
        onClosed: (answered: Boolean) -> Unit,
    ): Boolean {
        val ui = UpdaterUi(activity)
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
        return UpdaterUi.present(activity, dialog) { answered -> if (!updating) onClosed(answered) }
    }

    fun message(activity: Activity, text: String) {
        val ui = UpdaterUi(activity)
        val content = ui.content()
        content.addView(ui.body(text))

        val dialog = AlertDialog.Builder(activity).setView(content).create()
        content.addView(ui.buttons(ui.pill(activity.getString(android.R.string.ok)) { dialog.dismiss() }))
        UpdaterUi.present(activity, dialog)
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
        val ui = UpdaterUi(activity)
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
        return UpdaterUi.present(activity, dialog) { onClosed() }
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
            val ui = UpdaterUi(activity)
            label = ui.body(strings.downloading)
            bar = progressBar(ui)

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
        fun show(): Boolean = UpdaterUi.present(activity, dialog)

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
