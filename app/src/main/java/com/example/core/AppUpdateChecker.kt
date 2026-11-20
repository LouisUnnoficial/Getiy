package com.example.core

import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String? = null,
    val isDraft: Boolean = false,
)

sealed class UpdateCheckOutcome {
    data class UpToDate(val latestTag: String?) : UpdateCheckOutcome()
    data class UpdateAvailable(val release: GitHubReleaseInfo) : UpdateCheckOutcome()
    data class Failed(val message: String) : UpdateCheckOutcome()
}

/**
 * Checks the configured GitHub repository for the latest published release and
 * compares its version against the currently installed app versionName.
 */
class AppUpdateChecker(
    private val repoOwner: String = DEFAULT_REPO_OWNER,
    private val repoName: String = DEFAULT_REPO_NAME,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {
    private val latestReleaseApiUrl: String
        get() = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"

    private val releasesListApiUrl: String
        get() = "https://api.github.com/repos/$repoOwner/$repoName/releases?per_page=5"

    private val fallbackReleasePageUrl: String
        get() = "https://github.com/$repoOwner/$repoName/releases/latest"

    fun checkForUpdate(currentVersion: String): UpdateCheckOutcome {
        return runCatching {
            val latest = fetchLatestRelease(currentVersion)
                ?: return@runCatching UpdateCheckOutcome.UpToDate(latestTag = null)

            val remoteVersion = latest.tagName.ifBlank { latest.releaseName }
            if (isUpdateAvailable(currentVersion = currentVersion, remoteVersion = remoteVersion)) {
                UpdateCheckOutcome.UpdateAvailable(latest)
            } else {
                UpdateCheckOutcome.UpToDate(latestTag = remoteVersion.ifBlank { null })
            }
        }.getOrElse { err ->
            UpdateCheckOutcome.Failed(err.message ?: "Couldn't reach GitHub")
        }
    }

    private fun fetchLatestRelease(currentVersion: String): GitHubReleaseInfo? {
        val latestRequest = buildRequest(latestReleaseApiUrl, currentVersion)
        httpClient.newCall(latestRequest).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return null
                return parseReleaseJson(JSONObject(body), fallbackReleasePageUrl)
            }
            if (resp.code != 404) {
                throw IllegalStateException("GitHub returned HTTP ${resp.code}")
            }
        }

        // If /releases/latest returns 404 (e.g., only pre-releases or freshly tagged release exist),
        // inspect the releases list and pick the newest non-draft release.
        val listRequest = buildRequest(releasesListApiUrl, currentVersion)
        httpClient.newCall(listRequest).execute().use { resp ->
            if (resp.code == 404) return null
            if (!resp.isSuccessful) {
                throw IllegalStateException("GitHub returned HTTP ${resp.code}")
            }
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return null
            return parseFirstPublishedRelease(JSONArray(body), fallbackReleasePageUrl)
        }
    }

    private fun buildRequest(url: String, currentVersion: String): Request {
        return Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Getiy-Android/$currentVersion")
            .build()
    }

    companion object {
        const val DEFAULT_REPO_OWNER = "tpxlegend"
        const val DEFAULT_REPO_NAME = "Getiy"

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

        internal fun parseReleaseJson(
            obj: JSONObject,
            fallbackUrl: String = "https://github.com/$DEFAULT_REPO_OWNER/$DEFAULT_REPO_NAME/releases/latest",
        ): GitHubReleaseInfo? {
            if (obj.optBoolean("draft", false)) return null
            val tag = obj.optString("tag_name").trim()
            val name = obj.optString("name").trim()
            if (tag.isBlank() && name.isBlank()) return null
            val notes = obj.optString("body").trim()
            val htmlUrl = obj.optString("html_url").trim().ifBlank { fallbackUrl }
            val apkUrl = extractApkAssetUrl(obj.optJSONArray("assets"))
            return GitHubReleaseInfo(
                tagName = tag.ifBlank { name },
                releaseName = name.ifBlank { tag },
                releaseNotes = notes,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                isDraft = false,
            )
        }

        internal fun parseFirstPublishedRelease(
            releases: JSONArray,
            fallbackUrl: String = "https://github.com/$DEFAULT_REPO_OWNER/$DEFAULT_REPO_NAME/releases/latest",
        ): GitHubReleaseInfo? {
            for (i in 0 until releases.length()) {
                val obj = releases.optJSONObject(i) ?: continue
                val parsed = parseReleaseJson(obj, fallbackUrl)
                if (parsed != null) return parsed
            }
            return null
        }

        private fun extractApkAssetUrl(assets: JSONArray?): String? {
            if (assets == null) return null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name")
                val downloadUrl = asset.optString("browser_download_url")
                if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                    return downloadUrl
                }
            }
            return null
        }

        /**
         * Normalizes a version string by trimming whitespace, stripping leading 'v'/'V'
         * or common 'release-' prefixes, and dropping '+build' metadata.
         */
        fun normalizeVersion(raw: String): String {
            var cleaned = raw.trim()
            if (cleaned.startsWith("v", ignoreCase = true) && cleaned.length > 1 && cleaned[1].isDigit()) {
                cleaned = cleaned.substring(1)
            }
            val plusIndex = cleaned.indexOf('+')
            if (plusIndex >= 0) {
                cleaned = cleaned.substring(0, plusIndex)
            }
            return cleaned.trim()
        }

        /**
         * Returns true when [remoteVersion] represents a newer or different release than [currentVersion].
         * If the normalized version names match (e.g., "1.0.0" vs "v1.0.0"), returns false so no popup is shown.
         */
        fun isUpdateAvailable(currentVersion: String, remoteVersion: String): Boolean {
            val currentNorm = normalizeVersion(currentVersion)
            val remoteNorm = normalizeVersion(remoteVersion)
            if (remoteNorm.isBlank()) return false
            if (currentNorm.equals(remoteNorm, ignoreCase = true)) return false

            val currentParsed = parseSemver(currentNorm)
            val remoteParsed = parseSemver(remoteNorm)

            if (currentParsed != null && remoteParsed != null) {
                return remoteParsed.compareTo(currentParsed) > 0
            }

            // Non-numeric custom release names: trigger update popup whenever they do not match.
            return true
        }

        private data class ParsedVersion(
            val numbers: List<Int>,
            val preRelease: String?,
        ) : Comparable<ParsedVersion> {
            override fun compareTo(other: ParsedVersion): Int {
                val maxLen = maxOf(numbers.size, other.numbers.size)
                for (i in 0 until maxLen) {
                    val a = numbers.getOrElse(i) { 0 }
                    val b = other.numbers.getOrElse(i) { 0 }
                    if (a != b) return a.compareTo(b)
                }
                return when {
                    preRelease == null && other.preRelease == null -> 0
                    preRelease == null && other.preRelease != null -> 1 // stable > pre-release
                    preRelease != null && other.preRelease == null -> -1
                    else -> preRelease!!.compareTo(other.preRelease!!, ignoreCase = true)
                }
            }
        }

        private fun parseSemver(normalized: String): ParsedVersion? {
            if (normalized.isBlank()) return null
            val dashIndex = normalized.indexOf('-')
            val corePart = if (dashIndex >= 0) normalized.substring(0, dashIndex) else normalized
            val prePart = if (dashIndex >= 0) normalized.substring(dashIndex + 1).ifBlank { null } else null

            val segments = corePart.split('.')
            if (segments.isEmpty()) return null
            val nums = segments.map { it.toIntOrNull() ?: return null }
            return ParsedVersion(numbers = nums, preRelease = prePart)
        }
    }
}
