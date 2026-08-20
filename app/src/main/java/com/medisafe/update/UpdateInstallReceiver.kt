package com.medisafe.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.medisafe.data.prefs.AppPreferences

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val tag = intent.getStringExtra(EXTRA_TAG).orEmpty()
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                } ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                if (tag.isNotBlank()) AppPreferences(context).installedReleaseTag = tag
            }
        }
    }

    companion object {
        const val ACTION_STATUS = "com.medisafe.app.UPDATE_INSTALL_STATUS"
        const val EXTRA_TAG = "extra_release_tag"
    }
}
