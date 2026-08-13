package com.fethica.swiftradio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These pin the outer recovery loop that sits above Media3's own per-load
 * retries. The bug they exist for was live on 2026-08-13: one listener logged
 * 354 failures on a single station across 43 minutes at a metronomic ~7s
 * interval, because the old loop reset its ladder on any momentary READY and
 * so never escalated past its shortest rungs.
 */
class PlaybackRetryPolicyTest {

    @Test fun `first retry waits one second`() =
        assertEquals(1_000L, PlaybackRetryPolicy.retryDelayMs(attempt = 1))

    @Test fun `each attempt doubles the wait`() {
        assertEquals(2_000L, PlaybackRetryPolicy.retryDelayMs(attempt = 2))
        assertEquals(4_000L, PlaybackRetryPolicy.retryDelayMs(attempt = 3))
        assertEquals(8_000L, PlaybackRetryPolicy.retryDelayMs(attempt = 4))
        assertEquals(16_000L, PlaybackRetryPolicy.retryDelayMs(attempt = 5))
    }

    @Test fun `wait is capped at thirty seconds`() {
        assertEquals(30_000L, PlaybackRetryPolicy.retryDelayMs(attempt = 6))
        assertEquals(30_000L, PlaybackRetryPolicy.retryDelayMs(attempt = 40))
    }

    @Test fun `a very large attempt count does not overflow into a short wait`() =
        assertEquals(30_000L, PlaybackRetryPolicy.retryDelayMs(attempt = 5_000))

    // THE REGRESSION TEST. A stream that connects, blips READY and drops again
    // must keep climbing the ladder -- resetting here is what produced the 7s
    // storm, because the ladder never left its 0ms/1s rungs.
    @Test fun `a brief ready does not reset the ladder`() =
        assertFalse(PlaybackRetryPolicy.shouldResetLadder(playedMs = 5_000L))

    @Test fun `sustained playback resets the ladder`() =
        assertTrue(PlaybackRetryPolicy.shouldResetLadder(playedMs = 30_000L))

    // Blind retrying with no network is pure battery burn; the connectivity
    // callback is what resumes playback, not the timer.
    @Test fun `no retry is scheduled while the device is offline`() =
        assertFalse(PlaybackRetryPolicy.shouldScheduleRetry(hasNetwork = false, permanentFailure = false))

    @Test fun `no retry is scheduled for a permanently failed stream`() =
        assertFalse(PlaybackRetryPolicy.shouldScheduleRetry(hasNetwork = true, permanentFailure = true))

    @Test fun `a retry is scheduled when online and the failure is transient`() =
        assertTrue(PlaybackRetryPolicy.shouldScheduleRetry(hasNetwork = true, permanentFailure = false))

    // Classification is what turns the error field into something actionable:
    // it is the difference between "the station's server is broken" and "this
    // listener walked into a tunnel". Codes are the Media3 PlaybackException
    // values, kept as plain Ints so this stays a pure unit test.
    @Test fun `a malformed container is permanent`() =
        assertTrue(PlaybackRetryPolicy.isPermanentFailure(errorCode = 3001, httpStatus = null))

    @Test fun `an unsupported format is permanent`() =
        assertTrue(PlaybackRetryPolicy.isPermanentFailure(errorCode = 3003, httpStatus = null))

    @Test fun `a missing file is permanent`() =
        assertTrue(PlaybackRetryPolicy.isPermanentFailure(errorCode = 2005, httpStatus = null))

    @Test fun `a 404 is permanent`() =
        assertTrue(PlaybackRetryPolicy.isPermanentFailure(errorCode = 2004, httpStatus = 404))

    @Test fun `a 500 is transient because the server may recover`() =
        assertFalse(PlaybackRetryPolicy.isPermanentFailure(errorCode = 2004, httpStatus = 500))

    // 408 and 429 are 4xx but explicitly mean "come back later".
    @Test fun `a 408 timeout is transient`() =
        assertFalse(PlaybackRetryPolicy.isPermanentFailure(errorCode = 2004, httpStatus = 408))

    @Test fun `a 429 rate limit is transient`() =
        assertFalse(PlaybackRetryPolicy.isPermanentFailure(errorCode = 2004, httpStatus = 429))

    @Test fun `a network connection failure is transient`() =
        assertFalse(PlaybackRetryPolicy.isPermanentFailure(errorCode = 2001, httpStatus = null))

    @Test fun `a network timeout is transient`() =
        assertFalse(PlaybackRetryPolicy.isPermanentFailure(errorCode = 2002, httpStatus = null))
}
