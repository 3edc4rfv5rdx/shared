package dev.about

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The About screen, built in code.
 *
 * It carries its own content and its own buttons rather than the platform's, and
 * the same black-on-white card ../updater draws: one dialog serves Compose apps
 * and Flutter apps alike, so its look is its own in all of them instead of
 * half-belonging to whichever toolkit the host happens to use.
 */
internal object AboutDialog {

    /** A dialog can only be shown on an activity that is still there to hold it. */
    private fun alive(activity: Activity): Boolean = !activity.isFinishing && !activity.isDestroyed

    /**
     * The pieces this dialog is made of, and the colours they use.
     *
     * Fixed rather than the host's: this code cannot ask the app which colours it
     * is drawing in — one that picks its theme in Compose leaves both the
     * system's night flag and the activity's XML theme saying something else —
     * and a dialog that guesses wrong is a dialog nobody can read.
     */
    private class Ui(val activity: Activity) {
        private val density = activity.resources.displayMetrics.density

        private val cardFill = 0xFFFFFFFF.toInt()
        private val pillFill = 0xFFFFFFFF.toInt()
        private val pillStroke = 0xFF000000.toInt()
        private val textColor = 0xFF000000.toInt()
        private val labelColor = 0xFF666666.toInt()
        private val linkColor = 0xFF0057B8.toInt()

        fun dp(value: Int): Int = (value * density).toInt()

        /**
         * The facts, in a scroller of their own.
         *
         * The buttons live outside it: a phone in landscape, or an app with rows
         * of its own to show, made the card taller than the screen and it was
         * the buttons at the bottom that went off it.
         */
        fun scroller(column: View): ScrollView = ScrollView(activity).apply {
            isFillViewport = false
            addView(
                column,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        /** A column of facts, to go inside the scroller. */
        fun column(): LinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

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

        /** A sentence, not a fact: no label above it and room under it. */
        fun paragraph(text: String): TextView = TextView(activity).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(textColor)
            setPadding(0, 0, 0, dp(16))
        }

        fun title(text: String): TextView = TextView(activity).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(textColor)
            setPadding(0, 0, 0, dp(16))
        }

        /**
         * One fact: its label above in grey, the value under it in black.
         *
         * Two lines rather than a label column, because the values here are of
         * every width — a version, a date, a mail address — and a column wide
         * enough for the longest of them leaves the short ones stranded.
         */
        fun row(label: String, value: String): LinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
            addView(
                TextView(activity).apply {
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(labelColor)
                }
            )
            addView(
                TextView(activity).apply {
                    text = value
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    setTextColor(textColor)
                }
            )
        }

        /** A row whose value is a link: underlined, and it opens something. */
        fun linkRow(label: String, value: String, onClick: () -> Unit): LinearLayout =
            row(label, value).apply {
                val text = getChildAt(1) as TextView
                text.setTextColor(linkColor)
                text.paintFlags = text.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                isClickable = true
                setOnClickListener { onClick() }
            }

        /**
         * A pill: fully rounded, because the radius is larger than the button can
         * ever be tall, so the ends stay semicircular whatever the text does to
         * the height.
         */
        fun pill(label: String, onClick: () -> Unit): TextView =
            TextView(activity).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setPadding(dp(14), dp(13), dp(14), dp(13))
                isClickable = true
                style(this, label, inverted = false)
                setOnClickListener { onClick() }
            }

        /**
         * What a pill says and how it is filled, in one place: the update button
         * is drawn once before the server has been asked and again with the
         * answer, and the two have to agree on everything but the two colours.
         */
        fun style(pill: TextView, label: String, inverted: Boolean) {
            pill.text = label
            pill.setTextColor(if (inverted) cardFill else textColor)
            pill.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(100).toFloat()
                setColor(if (inverted) textColor else pillFill)
                setStroke(dp(1), pillStroke)
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
     * Shows a dialog whose content already carries its own card: the window's own
     * background would otherwise sit behind it as a second, differently coloured
     * panel with square corners showing past the rounded ones.
     */
    private fun present(dialog: AlertDialog) {
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
    }

    /**
     * The dialog itself: what this app is, and the two places it can be answered
     * from.
     *
     * [onUpdate] is null when the app publishes no releases, and then the button
     * is simply not there — a check that cannot answer is worse than none.
     *
     * [onProbe] is that same app's silent look at the server, asked as the dialog
     * opens. Until it answers, and forever if it answers no, the button offers a
     * check; a yes turns it into the offer itself. It is only consulted when
     * there is a button to draw, and the two are given together.
     */
    fun show(
        activity: Activity,
        strings: AboutStrings,
        appName: String,
        description: String,
        facts: List<Pair<String, String>>,
        repoUrl: String,
        email: String,
        onOpen: (String) -> Unit,
        onMail: (String) -> Unit,
        onUpdate: (() -> Unit)?,
        onProbe: (((Boolean) -> Unit) -> Unit)? = null,
    ) {
        if (!alive(activity)) return
        val ui = Ui(activity)
        val content = ui.content()
        content.addView(ui.title(appName))

        val column = ui.column()
        if (description.isNotEmpty()) column.addView(ui.paragraph(description))
        facts.forEach { (label, value) -> column.addView(ui.row(label, value)) }
        column.addView(ui.linkRow("GitHub", repoUrl) { onOpen(repoUrl) })
        column.addView(ui.linkRow("E-mail", email) { onMail(email) })
        content.addView(
            ui.scroller(column),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        val dialog = AlertDialog.Builder(activity).setView(content).create()
        val pills = mutableListOf<View>()
        if (onUpdate != null) {
            val update = ui.pill(strings.checkUpdate) { dialog.dismiss(); onUpdate() }
            pills.add(update)
            // The answer lands while the dialog is up, on the main thread. A card
            // already dismissed by then simply keeps a view nobody is looking at.
            onProbe?.invoke { newer ->
                if (newer) ui.style(update, strings.update, inverted = true)
            }
        }
        pills.add(ui.pill(strings.close) { dialog.dismiss() })
        content.addView(ui.buttons(*pills.toTypedArray()))

        present(dialog)
    }
}
