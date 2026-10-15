package dev.updater

/**
 * The colours every dialog in these projects is drawn in — the updater's own and
 * ../about's alike.
 *
 * Fixed rather than the host's: this code cannot ask the app which colours it is
 * drawing in — one that picks its theme in Compose leaves both the system's
 * night flag and the activity's XML theme saying something else — and a dialog
 * that guesses wrong is a dialog nobody can read. Written once here because two
 * modules draw the same card, and a card that is violet in one of them and black
 * in the other reads as two different apps talking.
 */
object UpdaterColors {

    /** The card every dialog stands on. White, whatever the host is doing. */
    val card = 0xFFFFFFFF.toInt()

    /** Everything that is read rather than tapped. */
    val text = 0xFF000000.toInt()

    /** A label over its value: there to be found, never louder than the value. */
    val label = 0xFF666666.toInt()

    /** The one action a dialog is really offering, and the bar that runs it. */
    val accent = 0xFF6750A4.toInt()

    /** Text on top of [accent]. */
    val onAccent = 0xFFFFFFFF.toInt()

    /** Every other button: the same violet drained to a tint, and no outline. */
    val accentSoft = 0xFFEADDFF.toInt()

    /** Text on top of [accentSoft] — dark enough to be read at a glance. */
    val onAccentSoft = 0xFF4F378B.toInt()

    /**
     * The outline round every pill. The accent itself, so a filled button keeps
     * its edge exactly where the fill ends and a tinted one is drawn the same
     * shape rather than a lighter blob on the card.
     */
    val pillStroke = accent

    /** A link is text that acts, so it is drawn in the colour of the actions. */
    val link = accent

    /** The part of a progress bar that has not filled yet. */
    val track = accentSoft
}
