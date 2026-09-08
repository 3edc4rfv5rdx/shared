package dev.about

import java.util.Locale

/**
 * The dialog's own strings, in code rather than in res/values-*.
 *
 * The same reason as ../updater: shared sources are compiled into each app's
 * own namespace, so there is no R class of its own to import, and a release
 * build shrinks resources nothing statically references. Keeping the words here
 * is what makes one copy of this dialog work in every app.
 */
internal class AboutStrings private constructor(
    val version: String,
    val buildDate: String,
    val update: String,
    val close: String,
) {
    companion object {
        fun of(locale: Locale): AboutStrings = when (locale.language) {
            "ru" -> AboutStrings(
                version = "Версия",
                buildDate = "Дата сборки",
                update = "Обновить",
                close = "Закрыть",
            )

            "uk" -> AboutStrings(
                version = "Версія",
                buildDate = "Дата збірки",
                update = "Оновити",
                close = "Закрити",
            )

            else -> AboutStrings(
                version = "Version",
                buildDate = "Build date",
                update = "Update",
                close = "Close",
            )
        }
    }
}
