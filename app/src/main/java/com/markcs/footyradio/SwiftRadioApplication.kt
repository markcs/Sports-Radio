package com.markcs.footyradio

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.markcs.footyradio.data.ArtworkService
import com.markcs.footyradio.data.StationsRepository
import android.content.pm.ApplicationInfo
import com.markcs.footyradio.scores.SportsScoreManager
import com.markcs.footyradio.scores.SportsScoreProvider
import com.markcs.footyradio.scores.afl.SquiggleAflProvider
import com.markcs.footyradio.scores.afl.SquiggleKtorApi
import com.markcs.footyradio.scores.afl.SystemScoreClock
import com.markcs.footyradio.scores.mock.MockScoreProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class SwiftRadioApplication : Application(), SingletonImageLoader.Factory {

    private val applicationJob = SupervisorJob()
    val applicationScope = CoroutineScope(applicationJob + Dispatchers.Main)

    val sharedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = if (originalRequest.header("User-Agent") == null) {
                    originalRequest.newBuilder()
                        .header("User-Agent", Config.userAgent)
                        .build()
                } else {
                    originalRequest
                }

                val response = chain.proceed(requestWithUserAgent)
                if (response.code == 403) {
                    response.close()
                    val fallbackRequest = originalRequest.newBuilder()
                        .header("User-Agent", Config.fallbackUserAgent)
                        .build()
                    chain.proceed(fallbackRequest)
                } else {
                    response
                }
            }
            .build()
    }

    val stationsRepository: StationsRepository by lazy {
        StationsRepository(this, sharedOkHttpClient)
    }

    val teamsRepository: com.markcs.footyradio.data.TeamsRepository by lazy {
        com.markcs.footyradio.data.TeamsRepository(this, sharedOkHttpClient)
    }

    val artworkService: ArtworkService by lazy {
        ArtworkService(sharedOkHttpClient)
    }

    /** Debug builds use synthetic scores so the UI can be tested out of season. */
    private val isDebuggable: Boolean by lazy {
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    val sportsScoreManager: SportsScoreManager by lazy {
        val provider: SportsScoreProvider = if (isDebuggable) {
            MockScoreProvider(applicationScope)
        } else {
            SquiggleAflProvider(
                api = SquiggleKtorApi(sharedOkHttpClient),
                clock = SystemScoreClock,
                shortNames = teamsRepository.teamsMap,
                scope = applicationScope,
            )
        }
        SportsScoreManager(
            providers = mapOf(provider.sportId to provider),
            prefs = getSharedPreferences("footy_radio_prefs", MODE_PRIVATE),
            scope = applicationScope,
        )
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                // AssetManagerFetcher is usually added automatically on Android, 
                // but we'll ensure it's there alongside our custom OkHttp fetcher.
                add(OkHttpNetworkFetcherFactory(callFactory = { sharedOkHttpClient }))
            }
            .build()
    }
}
