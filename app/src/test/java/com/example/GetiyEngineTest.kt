package com.example

import com.example.core.Links
import com.example.data.engine.GetiyEngine
import com.example.data.model.DownloadMode
import com.example.data.model.MediaHelpers
import com.example.data.model.SubtitleChoice
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class GetiyEngineTest {

    @Test
    fun `applyArgsToRequest pairs video and subtitle options properly`() {
        val args = MediaHelpers.buildDownloadArgs(
            mode = DownloadMode.VIDEO,
            maxHeight = 1080,
            container = "mp4",
            subtitles = SubtitleChoice(langs = setOf("en", "es"), includeAuto = true, embed = true),
            trimStart = 12.5,
            trimEnd = 45.2,
        )
        val req = YoutubeDLRequest("https://example.com/watch?v=test")
        GetiyEngine.applyArgsToRequest(req, args)

        assertEquals("bv*[height<=1080]+ba/b[height<=1080]/bv*+ba/b", req.getOption("-f"))
        assertEquals("res,vcodec:h264,acodec:aac", req.getOption("-S"))
        assertEquals("mp4", req.getOption("--merge-output-format"))
        assertEquals("en,es", req.getOption("--sub-langs"))
        assertEquals("srt", req.getOption("--convert-subs"))
        assertEquals("*12.5-45.2", req.getOption("--download-sections"))

        val cmd = req.buildCommand()
        assertTrue(cmd.contains("--write-subs"))
        assertTrue(cmd.contains("--write-auto-subs"))
        assertTrue(cmd.contains("--embed-subs"))
        assertTrue(cmd.contains("--force-keyframes-at-cuts"))
        val fIndex = cmd.indexOf("-f")
        assertTrue(fIndex >= 0)
        assertEquals("bv*[height<=1080]+ba/b[height<=1080]/bv*+ba/b", cmd[fIndex + 1])
    }

    @Test
    fun `4K video download args preserve resolution priority over h264`() {
        val args = MediaHelpers.buildDownloadArgs(
            mode = DownloadMode.VIDEO,
            maxHeight = 2160,
            container = "mp4",
        )
        val req = YoutubeDLRequest("https://www.youtube.com/watch?v=4ktest")
        GetiyEngine.applyArgsToRequest(req, args)

        assertEquals("bv*[height<=2160]+ba/b[height<=2160]/bv*+ba/b", req.getOption("-f"))
        assertEquals("res,acodec:aac,ext:mp4:m4a", req.getOption("-S"))
        assertEquals("mp4", req.getOption("--merge-output-format"))
    }

    @Test
    fun `applyArgsToRequest pairs audio extraction flags properly`() {
        val args = MediaHelpers.buildDownloadArgs(
            mode = DownloadMode.AUDIO_ONLY,
            maxHeight = null,
            audioFormat = "mp3",
        )
        val req = YoutubeDLRequest("https://example.com/audio")
        GetiyEngine.applyArgsToRequest(req, args)

        assertEquals("ba/b", req.getOption("-f"))
        assertEquals("mp3", req.getOption("--audio-format"))
        assertEquals("0", req.getOption("--audio-quality"))
        val cmd = req.buildCommand()
        assertTrue(cmd.contains("-x"))
    }

    @Test
    fun `trim timestamps stay locale independent under comma-decimal locales`() {
        val prev = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val args = MediaHelpers.buildDownloadArgs(
                mode = DownloadMode.VIDEO,
                maxHeight = 720,
                trimStart = 1.5,
                trimEnd = 10.2,
            )
            val sectionIdx = args.indexOf("--download-sections")
            assertTrue(sectionIdx >= 0)
            val range = args[sectionIdx + 1]
            assertEquals("*1.5-10.2", range)
            assertFalse(range.contains(","))
        } finally {
            Locale.setDefault(prev)
        }
    }

    @Test
    fun `parseMediaInfoJson unwraps single-entry posts and infers omitted codecs for Instagram TikTok and X`() {
        val json = """
            {
              "_type": "playlist",
              "extractor_key": "Instagram",
              "title": "Sunset Reel",
              "entries": [
                {
                  "id": "reel_123",
                  "title": "Sunset Reel",
                  "webpage_url": "https://www.instagram.com/reel/ABC123/",
                  "formats": [
                    {
                      "format_id": "0",
                      "ext": "mp4",
                      "height": 1080,
                      "url": "https://cdn.instagram.com/v/video.mp4"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val info = MediaHelpers.parseMediaInfoJson(json, "https://www.instagram.com/reel/ABC123/")
        assertFalse(info.isPlaylist)
        assertEquals("Sunset Reel", info.title)
        assertEquals("Instagram", info.site)
        assertEquals(1, info.formats.size)
        assertTrue(info.formats.first().hasVideo)
        assertTrue(info.formats.first().hasAudio)
        assertEquals(1080, info.formats.first().height)
        assertNotNull(info.previewFormat)
    }

    @Test
    fun `parseMediaInfoJson synthesizes format from direct root url when formats array is absent`() {
        val json = """
            {
              "id": "pin_99",
              "title": "Design Inspiration",
              "extractor_key": "Pinterest",
              "ext": "mp4",
              "height": 720,
              "url": "https://v1.pinimg.com/videos/mc/720p/sample.mp4"
            }
        """.trimIndent()

        val info = MediaHelpers.parseMediaInfoJson(json, "https://www.pinterest.com/pin/99/")
        assertEquals(1, info.formats.size)
        assertTrue(info.formats.first().hasVideo)
        assertEquals(720, info.formats.first().height)
        assertNotNull(info.previewFormat)
    }

    @Test
    fun `extractMediaFromHtml extracts og video fallback cleanly`() {
        val html = """
            <html>
              <head>
                <meta property="og:title" content="Cool Pin Video" />
                <meta property="og:image" content="https://cdn.example.com/thumb.jpg" />
                <meta property="og:video:secure_url" content="https://cdn.example.com/clip.mp4?token=1&amp;hd=1" />
                <meta property="og:video:height" content="1080" />
              </head>
            </html>
        """.trimIndent()

        val info = GetiyEngine.extractMediaFromHtml(html, "https://www.pinterest.com/pin/123/", "Pinterest")
        assertNotNull(info)
        assertEquals("Cool Pin Video", info?.title)
        assertEquals("https://cdn.example.com/clip.mp4?token=1&hd=1", info?.url)
        assertEquals(1080, info?.formats?.firstOrNull()?.height)
    }

    @Test
    fun `Links recognizes broad platforms bare domains shortlinks and playlists`() {
        assertEquals("https://instagram.com/reel/ABC123/", Links.extractLink("instagram.com/reel/ABC123/"))
        assertEquals("Instagram", Links.platformOf("https://www.instagram.com/reel/ABC123/"))
        assertEquals("TikTok", Links.platformOf("https://vm.tiktok.com/ZM12345/"))
        assertEquals("Reddit", Links.platformOf("https://www.reddit.com/r/videos/comments/abc/title/"))
        assertEquals("X", Links.platformOf("https://x.com/user/status/12345?s=20&t=xyz"))
        assertEquals("Vimeo", Links.platformOf("https://vimeo.com/123456"))
        assertTrue(Links.isShortShareLink("https://vm.tiktok.com/ZM12345/"))
        assertTrue(Links.isShortShareLink("https://pin.it/abc123"))
        assertTrue(Links.looksLikePlaylistUrl("https://www.youtube.com/playlist?list=PL123"))
        assertFalse(Links.looksLikePlaylistUrl("https://www.instagram.com/reel/ABC123/"))
        assertFalse(Links.looksLikePlaylistUrl("https://www.youtube.com/watch?v=abc&list=PL123"))
    }

    @Test
    fun `parseFfmpegVersionLine extracts version token`() {
        assertEquals(
            "7.1.1-android",
            GetiyEngine.parseFfmpegVersionLine("ffmpeg version 7.1.1-android Copyright (c) 2000-2025"),
        )
        assertEquals(null, GetiyEngine.parseFfmpegVersionLine(""))
    }

    @Test
    fun `cleanEngineError extracts yt-dlp ERROR line`() {
        val err = RuntimeException("Traceback (most recent call last):\n  File ...\nERROR: [youtube] Private video. Sign in if you've been granted access")
        assertEquals(
            "[youtube] Private video. Sign in if you've been granted access",
            GetiyEngine.cleanEngineError(err),
        )
    }

    @Test
    fun `stripTrackingParams removes ad and share trackers while preserving functional params`() {
        val dirty = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=abc123&utm_source=share&fbclid=xyz&list=PL123"
        val cleaned = Links.stripTrackingParams(dirty)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123", cleaned)

        val igDirty = "https://www.instagram.com/reel/ABC123/?igsh=secret_tracker&utm_medium=copy_link"
        assertEquals("https://www.instagram.com/reel/ABC123/", Links.stripTrackingParams(igDirty))

        // Preserve shortlinks and functional tokens like X `s`/`t`
        val xUrl = "https://x.com/user/status/12345?s=20&t=abcToken"
        assertEquals(xUrl, Links.stripTrackingParams(xUrl))
    }

    @Test
    fun `EngineStatus starts in zero-overhead on-demand standby mode`() {
        val status = com.example.data.engine.EngineStatus()
        assertFalse(status.initializing)
        assertTrue(status.standby)
        assertTrue(status.summaryLabel.contains("On-demand", ignoreCase = true))
    }

    @Test
    fun `DeviceTier builds lean Media3 LoadControl for low-RAM devices`() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val mainControl = com.example.core.DeviceTier.buildLeanLoadControl(ctx, isInlinePreview = false)
        val previewControl = com.example.core.DeviceTier.buildLeanLoadControl(ctx, isInlinePreview = true)
        assertNotNull(mainControl)
        assertNotNull(previewControl)
    }
}
