package com.example

import com.example.core.AppUpdateChecker
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppUpdateCheckerTest {

    @Test
    fun exactAndPrefixedVersionMatchReturnsNoUpdate() {
        assertFalse(AppUpdateChecker.isUpdateAvailable("1.0.0", "1.0.0"))
        assertFalse(AppUpdateChecker.isUpdateAvailable("1.0.0", "v1.0.0"))
        assertFalse(AppUpdateChecker.isUpdateAvailable("1.0.0", "V1.0.0"))
        assertFalse(AppUpdateChecker.isUpdateAvailable("1.0.0", " v1.0.0 "))
        assertFalse(AppUpdateChecker.isUpdateAvailable("1.0.0", "v1.0"))
    }

    @Test
    fun newerReleaseVersionTriggersUpdatePopup() {
        assertTrue(AppUpdateChecker.isUpdateAvailable("1.0.0", "v1.0.1"))
        assertTrue(AppUpdateChecker.isUpdateAvailable("1.0.0", "1.1.0"))
        assertTrue(AppUpdateChecker.isUpdateAvailable("1.0.0", "v2.0.0"))
        assertTrue(AppUpdateChecker.isUpdateAvailable("1.0.0", "v1.0.0.1"))
        assertTrue(AppUpdateChecker.isUpdateAvailable("1.0.0-beta1", "v1.0.0"))
        assertTrue(AppUpdateChecker.isUpdateAvailable("1.0.0-beta1", "v1.0.0-beta2"))
    }

    @Test
    fun olderRemoteReleaseDoesNotTriggerDowngradePopup() {
        assertFalse(AppUpdateChecker.isUpdateAvailable("1.2.0", "v1.0.0"))
        assertFalse(AppUpdateChecker.isUpdateAvailable("1.0.0", "v0.9.9"))
        assertFalse(AppUpdateChecker.isUpdateAvailable("1.0.0", "v1.0.0-beta1"))
    }

    @Test
    fun nonNumericCustomReleaseTagTriggersOnMismatch() {
        assertTrue(AppUpdateChecker.isUpdateAvailable("1.0.0", "release-2026-12"))
        assertFalse(AppUpdateChecker.isUpdateAvailable("release-2026-12", "release-2026-12"))
    }

    @Test
    fun parseReleaseJsonExtractsReleaseDetailsAndApkAsset() {
        val json = JSONObject(
            """
            {
              "tag_name": "v1.1.0",
              "name": "Getiy 1.1.0",
              "body": "- Faster yt-dlp parsing\n- Bug fixes",
              "html_url": "https://github.com/tpxlegend/Getiy/releases/tag/v1.1.0",
              "draft": false,
              "assets": [
                {
                  "name": "getiy-v1.1.0.apk",
                  "browser_download_url": "https://github.com/tpxlegend/Getiy/releases/download/v1.1.0/getiy-v1.1.0.apk"
                }
              ]
            }
            """.trimIndent(),
        )

        val parsed = AppUpdateChecker.parseReleaseJson(json)
        assertNotNull(parsed)
        assertEquals("v1.1.0", parsed?.tagName)
        assertEquals("Getiy 1.1.0", parsed?.releaseName)
        assertEquals("https://github.com/tpxlegend/Getiy/releases/tag/v1.1.0", parsed?.htmlUrl)
        assertEquals(
            "https://github.com/tpxlegend/Getiy/releases/download/v1.1.0/getiy-v1.1.0.apk",
            parsed?.apkDownloadUrl,
        )
    }

    @Test
    fun parseFirstPublishedReleaseSkipsDrafts() {
        val array = JSONArray(
            """
            [
              {
                "tag_name": "v2.0.0-draft",
                "draft": true
              },
              {
                "tag_name": "v1.2.0",
                "name": "Release 1.2.0",
                "body": "Public release",
                "html_url": "https://github.com/tpxlegend/Getiy/releases/tag/v1.2.0",
                "draft": false
              }
            ]
            """.trimIndent(),
        )

        val parsed = AppUpdateChecker.parseFirstPublishedRelease(array)
        assertNotNull(parsed)
        assertEquals("v1.2.0", parsed?.tagName)

        val onlyDraft = JSONArray("""[{"tag_name":"v9.9.9","draft":true}]""")
        assertNull(AppUpdateChecker.parseFirstPublishedRelease(onlyDraft))
    }
}
