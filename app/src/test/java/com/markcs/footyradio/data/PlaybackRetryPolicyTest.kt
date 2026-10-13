package com.markcs.footyradio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRetryPolicyTest {

    @Test
    fun testRetryDelayBackoff() {
        assertEquals(1_000L, PlaybackRetryPolicy.retryDelayMs(1))
        assertEquals(2_000L, PlaybackRetryPolicy.retryDelayMs(2))
        assertEquals(4_000L, PlaybackRetryPolicy.retryDelayMs(3))
        assertEquals(8_000L, PlaybackRetryPolicy.retryDelayMs(4))
        assertEquals(16_000L, PlaybackRetryPolicy.retryDelayMs(5))
        assertEquals(30_000L, PlaybackRetryPolicy.retryDelayMs(6))
        assertEquals(30_000L, PlaybackRetryPolicy.retryDelayMs(10))
    }

    @Test
    fun testShouldResetLadder() {
        assertFalse(PlaybackRetryPolicy.shouldResetLadder(29_999L))
        assertTrue(PlaybackRetryPolicy.shouldResetLadder(30_000L))
        assertTrue(PlaybackRetryPolicy.shouldResetLadder(60_000L))
    }

    @Test
    fun testPermanentFailureDetection() {
        // 404 is permanent
        assertTrue(PlaybackRetryPolicy.isPermanentFailure(2004, 404))
        // 403 is permanent
        assertTrue(PlaybackRetryPolicy.isPermanentFailure(2004, 403))
        // 408 & 429 are transient
        assertFalse(PlaybackRetryPolicy.isPermanentFailure(2004, 408))
        assertFalse(PlaybackRetryPolicy.isPermanentFailure(2004, 429))
        // 500, 503 are transient
        assertFalse(PlaybackRetryPolicy.isPermanentFailure(2004, 500))
        assertFalse(PlaybackRetryPolicy.isPermanentFailure(2004, 503))

        // Malformed container and file not found are permanent
        assertTrue(PlaybackRetryPolicy.isPermanentFailure(3001, null))
        assertTrue(PlaybackRetryPolicy.isPermanentFailure(2005, null))

        // Network connection issues are transient
        assertFalse(PlaybackRetryPolicy.isPermanentFailure(2001, null))
        assertFalse(PlaybackRetryPolicy.isPermanentFailure(2002, null))
    }
}
