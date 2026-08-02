package com.markcs.footyradio.data

import android.content.Context
import com.markcs.footyradio.Config
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import android.util.Log
import java.io.File
import kotlinx.serialization.encodeToString

class StationsRepository(
    private val context: Context,
    okHttpClient: OkHttpClient
) {
    private val prefs = context.getSharedPreferences("footy_radio_prefs", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    // Dedicated scope for fire-and-forget background network refreshes.
    // Uses SupervisorJob so a failed refresh never cancels the scope itself.
    private val backgroundRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getStationsUrl(): String {
        return prefs.getString("custom_stations_url", Config.stationsURL) ?: Config.stationsURL
    }

    fun setStationsUrl(url: String) {
        prefs.edit().putString("custom_stations_url", url).apply()
    }

    fun resetStationsUrl() {
        prefs.edit().remove("custom_stations_url").apply()
    }

    private fun getCacheFile(): File {
        return File(context.filesDir, "cached_stations.json")
    }

    private fun getCustomStationsFile(): File {
        return File(context.filesDir, "custom_stations.json")
    }

    fun loadCustomStations(): List<RadioStation> {
        return try {
            val file = getCustomStationsFile()
            if (file.exists()) {
                val jsonString = file.readText()
                json.decodeFromString<StationsResponse>(jsonString).station
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("StationsRepository", "Failed to load custom stations", e)
            emptyList()
        }
    }

    fun saveCustomStations(stations: List<RadioStation>) {
        try {
            val response = StationsResponse(stations)
            val jsonString = json.encodeToString(response)
            getCustomStationsFile().writeText(jsonString)
        } catch (e: Exception) {
            Log.e("StationsRepository", "Failed to save custom stations", e)
        }
    }

    /**
     * Load stations using a cache-first strategy:
     * - If [forceRefresh] is false (default) and a disk cache exists, return it immediately.
     *   A background network refresh is then kicked off to keep the cache warm for next time.
     * - If [forceRefresh] is true (e.g. pull-to-refresh, settings change), always fetch from
     *   the network first and fall back to cache on failure.
     * - On first install (no cache) a network fetch is always performed, guarded by an 8-second
     *   timeout so Android Auto never spins forever.
     */
    suspend fun loadStations(remoteUrl: String? = null, forceRefresh: Boolean = false): List<RadioStation> = withContext(Dispatchers.IO) {
        val targetUrl = remoteUrl ?: getStationsUrl()

        val cachedRaw = if (!forceRefresh) loadFromCache() else emptyList()

        val remoteStations: List<RadioStation> = if (cachedRaw.isNotEmpty()) {
            // Cache hit: schedule a background refresh so data stays current next time.
            Log.d("StationsRepository", "Cache hit — returning ${cachedRaw.size} cached stations; scheduling background refresh")
            backgroundRefreshScope.launch {
                try {
                    val refreshed = loadFromNetwork(targetUrl)
                    saveToCache(refreshed)
                    Log.d("StationsRepository", "Background refresh complete — ${refreshed.size} stations cached")
                } catch (e: Exception) {
                    Log.w("StationsRepository", "Background refresh failed (cached data still valid)", e)
                }
            }
            cachedRaw
        } else {
            // No cache (first install) or forced refresh: must go to the network.
            Log.d("StationsRepository", "No cache or forceRefresh — fetching from network")
            try {
                val fetched = if (!forceRefresh) {
                    // First install: apply a timeout so Android Auto doesn't spin forever.
                    withTimeoutOrNull(8_000L) { loadFromNetwork(targetUrl) }
                        ?: run {
                            Log.w("StationsRepository", "Network fetch timed out on first install")
                            null
                        }
                } else {
                    loadFromNetwork(targetUrl)
                }
                if (fetched != null) {
                    saveToCache(fetched)
                    fetched
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("StationsRepository", "Failed to fetch stations from $targetUrl", e)
                val fallback = loadFromCache()
                if (fallback.isNotEmpty()) {
                    Log.d("StationsRepository", "Using cached stations as fallback")
                    fallback
                } else {
                    throw e
                }
            }
        }
        
        val customStations = loadCustomStations()
        val allStations = remoteStations + customStations
        
        val uniqueStations = allStations.mapIndexed { index, station ->
            val finalId = if (station.id.isBlank()) "station_$index" else station.id
            
            // Normalize streams: if streamURLs is empty but legacyStreamURL is present, migrate it.
            val normalizedStreamURLs = if (station.streamURLs.isEmpty() && station.legacyStreamURL.isNotBlank()) {
                listOf(station.legacyStreamURL)
            } else {
                station.streamURLs
            }

            station.copy(id = finalId, streamURLs = normalizedStreamURLs).apply {
                resolvedImageUrl = when {
                    imageURL.startsWith("http") -> imageURL
                    imageURL.isNotBlank() -> {
                        try {
                            // Validate asset existence
                            context.assets.open(imageURL).use { }
                            "file:///android_asset/$imageURL"
                        } catch (e: Exception) {
                            Log.w("StationsRepository", "Asset '$imageURL' for station '${station.name}' not found; falling back to stationImage.png")
                            "file:///android_asset/stationImage.png"
                        }
                    }
                    else -> {
                        Log.w("StationsRepository", "Station '${station.name}' has blank imageURL; falling back to stationImage.png")
                        "file:///android_asset/stationImage.png"
                    }
                }
            }
        }
        
        uniqueStations
    }

    private suspend fun loadFromNetwork(url: String): List<RadioStation> {
        return client.get(url).body<StationsResponse>().station
    }

    private fun saveToCache(stations: List<RadioStation>) {
        try {
            val response = StationsResponse(stations)
            val jsonString = json.encodeToString(response)
            getCacheFile().writeText(jsonString)
        } catch (e: Exception) {
            Log.e("StationsRepository", "Failed to cache stations", e)
        }
    }

    private fun loadFromCache(): List<RadioStation> {
        return try {
            val file = getCacheFile()
            if (file.exists()) {
                val jsonString = file.readText()
                json.decodeFromString<StationsResponse>(jsonString).station
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("StationsRepository", "Failed to load cached stations", e)
            emptyList()
        }
    }
}
