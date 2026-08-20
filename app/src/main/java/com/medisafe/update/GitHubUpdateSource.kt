package com.medisafe.update

import com.medisafe.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val tag: String,
    val title: String,
    val changelog: String,
    val apkUrl: String,
    val apkName: String,
    val apkSizeBytes: Long
)

object GitHubUpdateSource {
    fun fetchLatest(): AppRelease {
        val url = "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
        val body = httpGet(url, accept = "application/vnd.github+json")
        return parse(body)
    }

    fun parse(json: String): AppRelease {
        val root = JSONObject(json)
        if (root.has("message") && !root.has("tag_name")) {
            error(root.optString("message", "Couldn't read GitHub releases."))
        }
        val tag = AppVersion.normalize(root.optString("tag_name"))
        require(tag.isNotBlank()) { "Release has no version tag." }
        val title = root.optString("name").ifBlank { "MediSafe $tag" }
        val changelog = prettifyChangelog(root.optString("body"))
        val assets = root.optJSONArray("assets") ?: error("Release has no files.")
        var apkUrl = ""
        var apkName = ""
        var apkSize = 0L
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = asset.optString("browser_download_url")
            if (url.isBlank()) continue
            val prefer = name.startsWith("MediSafe", ignoreCase = true)
            if (apkUrl.isBlank() || prefer) {
                apkUrl = url
                apkName = name
                apkSize = asset.optLong("size")
                if (prefer) break
            }
        }
        require(apkUrl.isNotBlank()) { "This release has no APK to install." }
        return AppRelease(
            tag = tag,
            title = title,
            changelog = changelog,
            apkUrl = apkUrl,
            apkName = apkName,
            apkSizeBytes = apkSize
        )
    }

    fun prettifyChangelog(raw: String): String {
        val cleaned = raw.lineSequence()
            .map { line ->
                line.replace("**", "")
                    .replace("__", "")
                    .replace(Regex("^#+\\s*"), "")
                    .replace(Regex("^\\*\\s+"), "• ")
                    .replace(Regex("^-\\s+"), "• ")
                    .trimEnd()
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return cleaned.ifBlank { "Bug fixes and improvements." }
    }

    fun httpGet(url: String, accept: String): String {
        val conn = open(url, accept)
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                error(message?.takeIf { it.isNotBlank() } ?: "GitHub returned $code")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    fun openDownload(url: String): HttpURLConnection = open(url, "application/octet-stream")

    private fun open(url: String, accept: String): HttpURLConnection {
        var current = url
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                useCaches = false
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", "MediSafe/${BuildConfig.VERSION_NAME}")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val next = conn.getHeaderField("Location")
                // A redirect without a Location can't be followed — hand back the live
                // connection (the caller will surface the odd status code) rather than
                // one we already disconnected.
                if (next.isNullOrBlank()) return conn
                conn.disconnect()
                current = next
            } else {
                return conn
            }
        }
        error("Too many redirects while contacting GitHub.")
    }
}
