package com.markcs.footyradio

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioServiceMimeTypeTest {

    @Test
    fun testTripleMStreamMimeTypes() {
        // Triple M primary stream is a direct AAC stream without extension
        // It must NOT be inferred as HLS (M3U8) because that causes ExoPlayer's HlsPlaylistParser
        // to fail with "Input does not start with the #EXTM3U header".
        val primaryMime = AudioService.inferMimeType("https://sa47.scastream.com.au/3mmm_128")
        assertNull(primaryMime)

        // Triple M fallback stream is an HLS playlist
        val fallbackMime = AudioService.inferMimeType("https://sa47.scastream.com.au/live/3mmm_128.stream/playlist.m3u8")
        assertEquals(MimeTypes.APPLICATION_M3U8, fallbackMime)
    }

    @Test
    fun testMimeTypeInferenceForVariousStations() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            AudioService.inferMimeType("https://playerservices.streamtheworld.com/api/livestream-redirect/NOVA_100_AAC128.m3u8")
        )
        assertEquals(
            MimeTypes.AUDIO_MPEG,
            AudioService.inferMimeType("https://live-radio01.mediahubaustralia.com/3LRW/mp3/;")
        )
        assertEquals(
            MimeTypes.AUDIO_MPEG,
            AudioService.inferMimeType("http://13.54.221.214:8000/sen.mp3")
        )
        assertEquals(
            MimeTypes.AUDIO_MPEG,
            AudioService.inferMimeType("https://playerservices.streamtheworld.com/api/livestream-redirect/3AW.mp3")
        )
        assertEquals(
            "audio/aac",
            AudioService.inferMimeType("https://playerservices.streamtheworld.com/api/livestream-redirect/T_RAD_HITS_S01AAC.aac")
        )
    }

    @Test
    fun testContentTypeHeaderOverride() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            AudioService.inferMimeType("https://example.com/custom", "application/vnd.apple.mpegurl")
        )
        assertEquals(
            MimeTypes.AUDIO_MPEG,
            AudioService.inferMimeType("https://example.com/custom", "audio/mpeg")
        )
        assertEquals(
            "audio/aac",
            AudioService.inferMimeType("https://example.com/custom", "audio/aac")
        )
    }
}
