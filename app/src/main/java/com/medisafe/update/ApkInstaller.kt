package com.medisafe.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream

object ApkInstaller {
    const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun unknownSourcesIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun install(context: Context, apk: File, tag: String) {
        if (!apk.exists() || apk.length() < 64) {
            error("Downloaded APK is missing or empty.")
        }
        runCatching { installWithSession(context, apk, tag) }
            .getOrElse { installWithViewIntent(context, apk) }
    }

    private fun installWithSession(context: Context, apk: File, tag: String) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            session.openWrite("medisafe", 0, apk.length()).use { out ->
                FileInputStream(apk).use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            val statusIntent = Intent(context, UpdateInstallReceiver::class.java).apply {
                action = UpdateInstallReceiver.ACTION_STATUS
                putExtra(UpdateInstallReceiver.EXTRA_TAG, tag)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
            val pending = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
            session.commit(pending.intentSender)
        } finally {
            session.close()
        }
    }

    private fun installWithViewIntent(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + FILE_PROVIDER_SUFFIX,
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
