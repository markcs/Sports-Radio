package com.markcs.footyradio.scores.espn

import android.util.Log
import com.markcs.footyradio.Config
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private const val TAG = "EspnKtorApi"

/** Production fetcher for ESPN scoreboards. Returns null on any failure so the provider can retry. */
internal class EspnKtorApi(okHttpClient: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient.newBuilder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, Config.userAgent)
        }
    }

    suspend fun scoreboard(leaguePath: String): EspnScoreboard? = try {
        val body = client
            .get("https://site.api.espn.com/apis/site/v2/sports/$leaguePath/scoreboard")
            .bodyAsText()
        json.decodeFromString(EspnScoreboard.serializer(), body)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Scoreboard fetch failed for $leaguePath", e)
        null
    }
}
