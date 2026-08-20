package com.zkrwatch.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/**
 * Relays the package installer's "needs user confirmation" step to the system UI.
 * When a session commit needs the user to approve the install, it broadcasts
 * [PackageInstaller.STATUS_PENDING_USER_ACTION] with the confirm intent to launch.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirm)
        }
    }

    companion object {
        const val ACTION = "com.zkrwatch.UPDATE_INSTALL_STATUS"
    }
}
