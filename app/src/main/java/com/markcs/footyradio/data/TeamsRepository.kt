package com.markcs.footyradio.data

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

@Serializable
data class TeamItem(
    val name: String,
    val shortName: String
)

@Serializable
data class TeamsResponse(
    val teams: List<TeamItem> = emptyList()
)

class TeamsRepository(
    private val context: Context,
    okHttpClient: OkHttpClient
) {
    private val TAG = "TeamsRepository"
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

    private val backgroundRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _teamsMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val teamsMap: StateFlow<Map<String, String>> = _teamsMap.asStateFlow()

    init {
        // Load initial cached / fallback data into memory
        backgroundRefreshScope.launch {
            loadTeams()
        }
    }

    fun isTeamAbbreviationsEnabled(): Boolean {
        return prefs.getBoolean("enable_team_abbreviations", Config.enableTeamAbbreviations)
    }

    fun setTeamAbbreviationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enable_team_abbreviations", enabled).apply()
    }

    fun getTeamsUrl(): String {
        return prefs.getString("custom_teams_url", Config.teamsURL) ?: Config.teamsURL
    }

    fun setTeamsUrl(url: String) {
        prefs.edit().putString("custom_teams_url", url).apply()
    }

    fun resetTeamsUrl() {
        prefs.edit().remove("custom_teams_url").apply()
    }

    private fun getCacheFile(): File {
        return File(context.filesDir, "cached_teams.json")
    }

    suspend fun loadTeams(remoteUrl: String? = null, forceRefresh: Boolean = false): Map<String, String> = withContext(Dispatchers.IO) {
        val targetUrl = remoteUrl ?: getTeamsUrl()
        val cached = if (!forceRefresh) loadFromCache() else emptyList()

        val teamsList: List<TeamItem> = if (cached.isNotEmpty()) {
            Log.d(TAG, "Cache hit — returning ${cached.size} cached teams; scheduling background refresh")
            backgroundRefreshScope.launch {
                try {
                    val refreshed = loadFromNetwork(targetUrl)
                    saveToCache(refreshed)
                    updateTeamsMap(refreshed)
                    Log.d(TAG, "Background refresh complete — ${refreshed.size} teams cached")
                } catch (e: Exception) {
                    Log.w(TAG, "Background refresh failed (cached data still valid)", e)
                }
            }
            cached
        } else {
            Log.d(TAG, "No cache or forceRefresh — fetching from network")
            try {
                val fetched = if (!forceRefresh) {
                    withTimeoutOrNull(8_000L) { loadFromNetwork(targetUrl) }
                        ?: run {
                            Log.w(TAG, "Network fetch timed out on first install")
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
                Log.e(TAG, "Failed to fetch teams from $targetUrl", e)
                val fallback = loadFromCache()
                if (fallback.isNotEmpty()) {
                    fallback
                } else {
                    emptyList()
                }
            }
        }

        val map = updateTeamsMap(teamsList)
        map
    }

    private fun updateTeamsMap(teamsList: List<TeamItem>): Map<String, String> {
        val map = teamsList.associate { it.name.lowercase() to it.shortName }
        _teamsMap.value = map
        return map
    }

    private suspend fun loadFromNetwork(url: String): List<TeamItem> {
        return client.get(url).body<TeamsResponse>().teams
    }

    private fun saveToCache(teams: List<TeamItem>) {
        try {
            val response = TeamsResponse(teams)
            val jsonString = json.encodeToString(response)
            getCacheFile().writeText(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache teams", e)
        }
    }

    private fun loadFromCache(): List<TeamItem> {
        return try {
            val file = getCacheFile()
            if (file.exists()) {
                val jsonString = file.readText()
                json.decodeFromString<TeamsResponse>(jsonString).teams
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached teams", e)
            emptyList()
        }
    }
}
