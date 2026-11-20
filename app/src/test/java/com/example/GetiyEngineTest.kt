package com.example

import com.example.data.engine.GetiyEngine
import com.example.data.model.DownloadMode
import com.example.data.model.MediaHelpers
import com.example.data.model.SubtitleChoice
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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
        assertEquals("vcodec:h264,acodec:aac", req.getOption("-S"))
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
    fun `stripTrackingParams removes ad and share trackers while preserving media IDs`() {
        val dirty = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=abc123&utm_source=share&fbclid=xyz&list=PL123"
        val cleaned = com.example.core.Links.stripTrackingParams(dirty)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123", cleaned)

        val igDirty = "https://www.instagram.com/reel/ABC123/?igsh=secret_tracker&utm_medium=copy_link"
        assertEquals("https://www.instagram.com/reel/ABC123/", com.example.core.Links.stripTrackingParams(igDirty))
    }
}
