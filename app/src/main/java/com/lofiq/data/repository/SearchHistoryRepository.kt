package com.lofiq.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("lofiq_search_history") }
    )
    private val searchHistoryKey = stringPreferencesKey("recent_search_queries")

    val searchHistory: Flow<List<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            val raw = prefs[searchHistoryKey] ?: return@map emptyList()
            try {
                val list = json.decodeFromString<List<String>>(raw)
                // Filter out any lingering fragments where a longer matching query exists
                list.filter { item ->
                    list.none { other -> other.length > item.length && other.startsWith(item, ignoreCase = true) }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun addQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return

        dataStore.edit { prefs ->
            val existing = try {
                val raw = prefs[searchHistoryKey]
                if (raw != null) json.decodeFromString<List<String>>(raw) else emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            // Filter out exact duplicates AND remove partial prefixes/substrings (e.g. "Pavi", "Pavizha Ma" when "Pavizha Mazha" is added)
            val updated = (listOf(trimmed) + existing.filterNot { item ->
                item.equals(trimmed, ignoreCase = true) ||
                (trimmed.startsWith(item, ignoreCase = true) && item.length < trimmed.length) ||
                (item.startsWith(trimmed, ignoreCase = true) && trimmed.length < item.length)
            }).take(25)

            prefs[searchHistoryKey] = json.encodeToString(updated)
        }
    }

    suspend fun removeQuery(query: String) {
        dataStore.edit { prefs ->
            val existing = try {
                val raw = prefs[searchHistoryKey]
                if (raw != null) json.decodeFromString<List<String>>(raw) else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val updated = existing.filterNot { it.equals(query.trim(), ignoreCase = true) }
            prefs[searchHistoryKey] = json.encodeToString(updated)
        }
    }

    suspend fun clearHistory() {
        dataStore.edit { prefs ->
            prefs.remove(searchHistoryKey)
        }
    }
}
