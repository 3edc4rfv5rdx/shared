package dev.about

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.updater.UpdaterColors
import dev.updater.UpdaterUi

/**
 * The About screen, built in code on ../updater's [UpdaterUi]: the card, the
 * pills and the way on screen are the kit's, so the card the update button
 * opens is the card it came from. What is written here is only what About
 * shows that no other dialog does.
 */
internal object AboutDialog {

    /**
     * What is About's own on top of [UpdaterUi]: the facts. A sentence, a
     * labelled value, a value that opens something, and the scroller they go in.
     */

    /**
     * The facts, in a scroller of their own.
     *
     * The buttons live outside it: a phone in landscape, or an app with rows
     * of its own to show, made the card taller than the screen and it was
     * the buttons at the bottom that went off it.
     */
    private fun UpdaterUi.scroller(column: View): ScrollView = ScrollView(activity).apply {
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
    private fun UpdaterUi.column(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
    }

    /** A sentence, not a fact: no label above it and room under it. */
    private fun UpdaterUi.paragraph(text: String): TextView = TextView(activity).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTextColor(UpdaterColors.text)
        setPadding(0, 0, 0, dp(16))
    }

    /**
     * One fact: its label above in grey, the value under it in black.
     *
     * Two lines rather than a label column, because the values here are of
     * every width — a version, a date, a mail address — and a column wide
     * enough for the longest of them leaves the short ones stranded.
     */
    private fun UpdaterUi.row(label: String, value: String): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(12))
        addView(
            TextView(activity).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(UpdaterColors.label)
            }
        )
        addView(
            TextView(activity).apply {
                text = value
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTextColor(UpdaterColors.text)
            }
        )
    }

    /** A row whose value is a link: underlined, and it opens something. */
    private fun UpdaterUi.linkRow(label: String, value: String, onClick: () -> Unit): LinearLayout =
        row(label, value).apply {
            val text = getChildAt(1) as TextView
            text.setTextColor(UpdaterColors.link)
            text.paintFlags = text.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            isClickable = true
            setOnClickListener { onClick() }
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
        if (!UpdaterUi.alive(activity)) return
        val ui = UpdaterUi(activity)
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

        UpdaterUi.present(activity, dialog)
    }
}
