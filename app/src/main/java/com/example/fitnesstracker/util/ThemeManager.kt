package com.example.fitnesstracker.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object ThemeManager {
    private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode_enabled")
    
    /**
     * Get dark mode preference from DataStore
     * Defaults to true (dark mode) if not set
     */
    fun getDarkModePreference(dataStore: DataStore<Preferences>): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[DARK_MODE_KEY] ?: true // Default to dark mode
        }
    }
    
    /**
     * Set dark mode preference in DataStore
     */
    suspend fun setDarkModePreference(
        dataStore: DataStore<Preferences>,
        enabled: Boolean
    ) {
        try {
            dataStore.edit { preferences ->
                preferences[DARK_MODE_KEY] = enabled
            }
        } catch (e: Exception) {
            android.util.Log.e("ThemeManager", "Failed to save dark mode preference: ${e.message}")
            e.printStackTrace()
        }
    }
}

