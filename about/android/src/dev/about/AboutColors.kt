package dev.about

/**
 * The colours every dialog in these projects is drawn in — About's own and
 * ../updater's alike.
 *
 * The card and the text are fixed: this code cannot ask the app which colours
 * it is drawing in — one that picks its theme in Compose leaves both the
 * system's night flag and the activity's XML theme saying something else — and
 * a dialog that guesses wrong is a dialog nobody can read. The accent is the
 * one thing the app may hand over, through [About.accent]; every other shade is
 * worked out from it, so an app names one colour and the tint, the text on it
 * and the progress track still agree with each other.
 */
internal class AboutColors private constructor(
    /** The one action the dialog is really offering. */
    val accent: Int,
) {
    /** The card the dialog stands on. White, whatever the host is doing. */
    val card = WHITE

    /** Everything that is read rather than tapped. */
    val text = BLACK

    /** A label over its value: there to be found, never louder than the value. */
    val label = 0xFF666666.toInt()

    /** Text on top of [accent]: whichever of white and black reads better on it. */
    val onAccent = if (contrast(accent, WHITE) >= contrast(accent, BLACK)) WHITE else BLACK

    /** Every other button: the accent drained to a tint. */
    val accentSoft = mix(accent, WHITE, SOFT_SHARE)

    /** Text on top of [accentSoft]: the accent darkened until it reads at a glance. */
    val onAccentSoft = mix(accent, BLACK, DARK_SHARE)

    /** The outline round every pill, so a tinted one keeps the filled one's shape. */
    val pillStroke = accent

    /** The part of ../updater's progress bar that has not filled yet. */
    val track = accentSoft

    /**
     * A link is text that acts, so it is drawn in the colour of the actions —
     * unless that colour is too pale to read on white, and then in the darker
     * shade the tinted buttons already write in.
     */
    val link = if (contrast(accent, WHITE) >= LINK_CONTRAST) accent else onAccentSoft

    companion object {
        /** Blue-grey: what every app gets that names no accent of its own. */
        private const val DEFAULT_ACCENT = 0xFF455A64.toInt()

        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val BLACK = 0xFF000000.toInt()

        /** How much of the accent is left in [accentSoft]; the rest is white. */
        private const val SOFT_SHARE = 0.25

        /** How much of the accent is left in [onAccentSoft]; the rest is black. */
        private const val DARK_SHARE = 0.55

        /** The least contrast with the white card a link is drawn in the accent at. */
        private const val LINK_CONTRAST = 3.0

        /**
         * The palette for the accent the app has set right now, or the blue-grey
         * one when it has set none. Asked each time a dialog is built, so a
         * dialog put up after the app changed its accent is already in the new
         * one. The alpha is dropped: a half-transparent accent would let the
         * window behind show through a button.
         */
        fun current(): AboutColors =
            AboutColors((About.accent ?: DEFAULT_ACCENT) or 0xFF000000.toInt())

        /** [color] with [share] of itself left and the rest made of [base]. */
        private fun mix(color: Int, base: Int, share: Double): Int {
            fun channel(shift: Int): Int {
                val c = (color shr shift) and 0xFF
                val b = (base shr shift) and 0xFF
                return (c * share + b * (1 - share) + 0.5).toInt() shl shift
            }
            return 0xFF000000.toInt() or channel(16) or channel(8) or channel(0)
        }

        /** The WCAG contrast ratio of two opaque colours, 1 to 21. */
        private fun contrast(a: Int, b: Int): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        /** Relative luminance, by hand: Color.luminance needs API 24 and a Flutter host may run lower. */
        private fun luminance(color: Int): Double {
            fun linear(shift: Int): Double {
                val c = ((color shr shift) and 0xFF) / 255.0
                return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
        }
    }
}
