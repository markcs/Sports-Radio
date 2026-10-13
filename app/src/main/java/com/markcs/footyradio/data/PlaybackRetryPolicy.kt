package com.markcs.footyradio.data

/**
 * The OUTER recovery loop for live streams.
 *
 * Media3 already retries each failed load itself -- `DefaultLoadErrorHandlingPolicy`
 * allows 6 attempts for progressive live sources and spaces them
 * `min((errorCount - 1) * 1000, 5000)` apart -- and only raises `onPlayerError`
 * once those are exhausted. This policy governs what happens *after* that, i.e.
 * how long to wait before re-preparing the player, and it deliberately does not
 * duplicate the inner ladder.
 *
 * Kept pure and free of Android types so it is unit-testable; `AudioService` is
 * a Service and cannot be exercised directly.
 */
object PlaybackRetryPolicy {
    const val BASE_DELAY_MS = 1_000L
    const val MAX_DELAY_MS = 30_000L

    /**
     * How long playback must actually run before a stream counts as recovered.
     * Resetting on `STATE_READY` allows a flapping stream to restart the ladder
     * forever without escalating delay. Requiring real playing time is the fix.
     */
    const val STABLE_PLAYBACK_MS = 30_000L

    /** Exponential backoff, 1s doubling to a 30s ceiling. */
    fun retryDelayMs(attempt: Int): Long {
        if (attempt <= 1) return BASE_DELAY_MS
        val doublings = attempt - 1
        if (doublings >= 31) return MAX_DELAY_MS
        return (BASE_DELAY_MS shl doublings).coerceAtMost(MAX_DELAY_MS)
    }

    /** True only after [STABLE_PLAYBACK_MS] of real playback, never on a READY blip. */
    fun shouldResetLadder(playedMs: Long): Boolean = playedMs >= STABLE_PLAYBACK_MS

    /**
     * Offline retries are pure battery burn. Permanent failures (bad container, 404)
     * never recover by being asked again.
     */
    fun shouldScheduleRetry(hasNetwork: Boolean, permanentFailure: Boolean): Boolean =
        hasNetwork && !permanentFailure

    // Media3 PlaybackException codes inlined as Ints
    private const val ERROR_IO_NETWORK_CONNECTION_FAILED = 2001
    private const val ERROR_IO_NETWORK_CONNECTION_TIMEOUT = 2002
    private const val ERROR_IO_BAD_HTTP_STATUS = 2004
    private const val ERROR_IO_FILE_NOT_FOUND = 2005
    private const val ERROR_PARSING_CONTAINER_MALFORMED = 3001
    private const val ERROR_PARSING_CONTAINER_UNSUPPORTED = 3003

    /**
     * Whether asking again could ever help. Permanent failures stop the loop.
     */
    fun isPermanentFailure(errorCode: Int, httpStatus: Int?): Boolean = when (errorCode) {
        ERROR_PARSING_CONTAINER_MALFORMED,
        ERROR_PARSING_CONTAINER_UNSUPPORTED,
        ERROR_IO_FILE_NOT_FOUND -> true
        ERROR_IO_BAD_HTTP_STATUS -> isPermanentHttpStatus(httpStatus)
        ERROR_IO_NETWORK_CONNECTION_FAILED,
        ERROR_IO_NETWORK_CONNECTION_TIMEOUT -> false
        else -> false
    }

    /**
     * 4xx means the request itself is wrong, so repeating it is pointless --
     * except 408 and 429, which explicitly mean "try again later". 5xx is worth retrying.
     */
    private fun isPermanentHttpStatus(status: Int?): Boolean {
        if (status == null) return false
        if (status == 408 || status == 429) return false
        return status in 400..499
    }

    // Player.PLAY_WHEN_READY_CHANGE_REASON_* inlined as Ints
    const val PLAY_WHEN_READY_REASON_USER_REQUEST = 1
    const val PLAY_WHEN_READY_REASON_AUDIO_FOCUS_LOSS = 2
    const val PLAY_WHEN_READY_REASON_AUDIO_BECOMING_NOISY = 3
    const val PLAY_WHEN_READY_REASON_REMOTE = 4

    fun shouldRecoverFromIdle(
        playWhenReady: Boolean,
        recoveryStopInProgress: Boolean,
        errorReported: Boolean
    ): Boolean = playWhenReady && !recoveryStopInProgress && !errorReported

    fun shouldCancelRecoveryOnPlayWhenReady(playWhenReady: Boolean, reason: Int): Boolean =
        !playWhenReady && reason in PAUSE_REASONS_THAT_END_RECOVERY

    private val PAUSE_REASONS_THAT_END_RECOVERY = setOf(
        PLAY_WHEN_READY_REASON_USER_REQUEST,
        PLAY_WHEN_READY_REASON_AUDIO_BECOMING_NOISY,
        PLAY_WHEN_READY_REASON_REMOTE
    )
}
