package dev.updater

import java.util.Locale

/**
 * The updater's own strings, in code rather than in res/values-*.
 *
 * Shared sources are compiled into each app's own namespace, so this code has no
 * R class of its own to import, and reaching the app's one by name would put the
 * strings out of the resource shrinker's sight: every project builds release with
 * isShrinkResources = true, which drops resources nothing statically references.
 * Keeping them here is what makes one copy of the updater work in eight apps.
 */
internal class UpdaterStrings private constructor(
    val title: String,
    val fieldVersion: String,
    val fieldBuild: String,
    val update: String,
    val cancel: String,
    val later: String,
    val downloading: String,
    val upToDate: String,
    val errorNetwork: String,
    val errorCorrupt: String,
    val installSourcesTitle: String,
    val installSourcesBody: String,
    val openSettings: String,
) {
    companion object {
        fun of(locale: Locale): UpdaterStrings = when (locale.language) {
            "ru" -> UpdaterStrings(
                title = "Есть обновление",
                fieldVersion = "Версия",
                fieldBuild = "Сборка",
                update = "Обновить",
                cancel = "Отмена",
                later = "Позже",
                downloading = "Загрузка…",
                upToDate = "Установлена последняя версия",
                errorNetwork = "Сервер обновлений недоступен",
                errorCorrupt = "Файл загружен с ошибкой",
                installSourcesTitle = "Нужно разрешение",
                installSourcesBody = "Чтобы установить обновление, разрешите этому приложению устанавливать пакеты",
                openSettings = "Настройки",
            )

            "uk" -> UpdaterStrings(
                title = "Є оновлення",
                fieldVersion = "Версія",
                fieldBuild = "Збірка",
                update = "Оновити",
                cancel = "Скасувати",
                later = "Пізніше",
                downloading = "Завантаження…",
                upToDate = "Встановлено останню версію",
                errorNetwork = "Сервер оновлень недоступний",
                errorCorrupt = "Файл завантажено з помилкою",
                installSourcesTitle = "Потрібен дозвіл",
                installSourcesBody = "Щоб встановити оновлення, дозвольте цьому застосунку встановлювати пакети",
                openSettings = "Налаштування",
            )

            else -> UpdaterStrings(
                title = "Update available",
                fieldVersion = "Version",
                fieldBuild = "Build",
                update = "Update",
                cancel = "Cancel",
                later = "Later",
                downloading = "Downloading…",
                upToDate = "You are on the latest version",
                errorNetwork = "Update server unreachable",
                errorCorrupt = "Download was corrupted",
                installSourcesTitle = "Permission needed",
                installSourcesBody = "To install the update, allow this app to install packages",
                openSettings = "Settings",
            )
        }
    }
}
