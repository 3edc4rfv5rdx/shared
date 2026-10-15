package dev.updater

import androidx.core.content.FileProvider

/**
 * The updater's own FileProvider.
 *
 * A manifest names each component once, and several of these apps already
 * declare androidx.core.content.FileProvider for their own sharing — a second
 * declaration of the same class is a duplicate component, not a second
 * provider. A subclass is a different name, so the app's provider and the
 * updater's live side by side, each with its own authority and its own paths.
 *
 * It stays empty on purpose: the paths come from the meta-data in the manifest,
 * which is where a resource reference can be written at all — shared sources
 * have no R of their own to point at @xml/updater_file_paths from here.
 */
class UpdaterFileProvider : FileProvider()
