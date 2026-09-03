package dev.updater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Handing a downloaded APK to the system installer.
 *
 * Nothing here decides anything: the user has already agreed in the dialog, and
 * Android asks once more on its own before it installs.
 */
internal object UpdaterInstall {

    /**
     * The authority declared in each host app's manifest. It carries the app's
     * own id because two apps on one phone cannot claim the same authority, and
     * these eight apps sit on the same phone.
     */
    fun authority(context: Context): String = "${context.packageName}.updater.fileprovider"

    /**
     * Since Android 8 an app installs an APK only after the user grants it that
     * right in Settings, once, per app. Nothing warns when it is missing: the
     * install intent simply does nothing, which is why it is checked up front.
     */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallSourcesSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${activity.packageName}"),
        )
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            // Some ROMs ship without that screen; the generic security page is
            // the closest thing to it and is better than dropping the tap.
            try {
                activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            } catch (ignored: Exception) {
            }
        }
    }

    fun install(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(activity, authority(activity), apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
}
