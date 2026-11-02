package com.markcs.footyradio.scores.afl

import android.util.Log
import com.markcs.footyradio.Config
import com.markcs.footyradio.data.SquiggleGame
import com.markcs.footyradio.data.SquiggleResponse
import com.markcs.footyradio.scores.ScoreClock
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private const val TAG = "SquiggleKtorApi"

/** Production [SquiggleApi]: Squiggle REST for the schedule, SSE for live updates. */
internal class SquiggleKtorApi(okHttpClient: OkHttpClient) : SquiggleApi {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // Disable read timeout for SSE
                .build()
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(SSE)
        install(HttpTimeout) {
            requestTimeoutMillis = Long.MAX_VALUE
            socketTimeoutMillis = Long.MAX_VALUE
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, Config.userAgent)
        }
    }

    override suspend fun fetchGames(year: Int): List<SquiggleGame>? = try {
        client.get("https://api.squiggle.com.au/?q=games;year=$year").body<SquiggleResponse>().games
    } catch (e: Exception) {
        Log.e(TAG, "Failed to fetch games for $year", e)
        null
    }

    override fun sseEvents(): Flow<SquiggleEvent> = flow {
        client.sse("https://sse.squiggle.com.au/events") {
            incoming.collect { ev ->
                parseSquiggleEvent(ev.event.orEmpty(), ev.data.orEmpty(), json)?.let { emit(it) }
            }
        }
    }
}

/** Wall-clock time source for production. */
object SystemScoreClock : ScoreClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
