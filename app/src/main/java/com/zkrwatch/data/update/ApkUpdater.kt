package com.zkrwatch.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads a release APK and hands it to the system package installer.
 *
 * Privacy: like [UpdateChecker], uses its own bare [OkHttpClient] with no auth and no
 * interceptors — it only GETs the public GitHub asset URL. The APK is written to the
 * app's private cache directory, never to shared storage.
 *
 * The installer requires the `REQUEST_INSTALL_PACKAGES` permission and a one-time
 * user grant of "install unknown apps"; the system shows its own confirm screen.
 */
object ApkUpdater {

    private val client = OkHttpClient()

    /** Downloads then launches the installer. Returns null on success, else a short error. */
    suspend fun downloadAndInstall(context: Context, info: UpdateInfo): String? {
        val app = context.applicationContext
        val apk = withContext(Dispatchers.IO) { download(app, info.apkUrl) }
            ?: return "Download failed"
        return runCatching { install(app, apk) }.exceptionOrNull()?.let {
            it.message ?: "Install failed"
        }
    }

    private fun download(context: Context, url: String): File? {
        val req = Request.Builder().url(url).header("User-Agent", "ZkrWatch-Updater").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            val out = File(context.cacheDir, "zkrwatch-update.apk")
            out.outputStream().use { sink -> body.byteStream().use { it.copyTo(sink) } }
            return out.takeIf { it.length() > 0 }
        }
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("zkrwatch", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // A mutable PendingIntent so the installer can fill in the status extras.
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val statusIntent = Intent(context, UpdateInstallReceiver::class.java)
                .setAction(UpdateInstallReceiver.ACTION)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                statusIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            session.commit(pending.intentSender)
        }
    }
}
