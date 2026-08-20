package com.zkrwatch.data.update

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A GitHub release newer than the installed build. */
data class UpdateInfo(
    val versionName: String, // e.g. "1.3.0"
    val tag: String, // e.g. "v1.3.0"
    val apkUrl: String, // public browser_download_url of the .apk asset
)

/**
 * Checks the project's **public** GitHub Releases for a newer version.
 *
 * Privacy: deliberately isolated from the vehicle API. It uses its own bare
 * [OkHttpClient] with **no interceptors, no auth token, no device id, and no account
 * data** — it never touches [com.zkrwatch.data.net.ZkrHttp] or any signed/session
 * headers. The only thing sent to GitHub is an anonymous request with a generic
 * User-Agent; nothing about the user or vehicle leaves the device.
 */
class UpdateChecker(
    private val currentVersionName: String,
    private val owner: String = REPO_OWNER,
    private val repo: String = REPO_NAME,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    /** The latest release if it's newer than [currentVersionName], else null (also on any error). */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val body = runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull() ?: return@withContext null

        runCatching { parse(body) }.getOrNull()
            ?.takeIf { isNewer(it.versionName, currentVersionName) }
    }

    private fun parse(json: String): UpdateInfo? {
        val map = mapAdapter.fromJson(json) ?: return null
        val tag = map["tag_name"] as? String ?: return null
        @Suppress("UNCHECKED_CAST")
        val assets = map["assets"] as? List<Map<String, Any?>> ?: emptyList()
        val apkUrl = assets
            .firstOrNull { (it["name"] as? String)?.endsWith(".apk", ignoreCase = true) == true }
            ?.get("browser_download_url") as? String
            ?: return null
        return UpdateInfo(versionName = tag.removePrefix("v"), tag = tag, apkUrl = apkUrl)
    }

    companion object {
        const val REPO_OWNER = "Fabbbrrr"
        const val REPO_NAME = "zkrwear"
        private const val USER_AGENT = "ZkrWatch-Updater"

        private val mapAdapter = Moshi.Builder().build().adapter<Map<String, Any?>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
        )

        /** True if dotted version [remote] is higher than [current] (e.g. 1.2.0 > 1.1.3). */
        fun isNewer(remote: String, current: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v")
                .split(".", "-", "_")
                .map { seg -> seg.takeWhile(Char::isDigit) }
                .mapNotNull { it.toIntOrNull() }
            val r = parts(remote)
            val c = parts(current)
            for (i in 0 until maxOf(r.size, c.size)) {
                val rv = r.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (rv != cv) return rv > cv
            }
            return false
        }
    }
}
