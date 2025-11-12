package com.example.fitnesstracker.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Helper class to manage current user session
 * Provides utilities to get current user ID from Firebase Auth or DataStore
 */
object UserSessionManager {
    private val USER_ID_KEY = stringPreferencesKey("user_id")
    private val ROOM_USER_ID_KEY = stringPreferencesKey("room_user_id")
    
    /**
     * Get current Firebase user ID
     * Returns null if no user is logged in
     */
    fun getCurrentFirebaseUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }
    
    /**
     * Get user ID from DataStore
     * This is the Firebase UID stored locally
     */
    suspend fun getUserIdFromDataStore(dataStore: DataStore<Preferences>): String? {
        return try {
            dataStore.data.map { preferences ->
                preferences[USER_ID_KEY]
            }.first()
        } catch (e: Exception) {
            android.util.Log.e("UserSessionManager", "Failed to get user ID from DataStore: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Get current user ID (tries Firebase first, then DataStore)
     */
    suspend fun getCurrentUserId(dataStore: DataStore<Preferences>): String? {
        return getCurrentFirebaseUserId() ?: getUserIdFromDataStore(dataStore)
    }
    
    /**
     * Check if user is logged in
     */
    fun isUserLoggedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }
    
    /**
     * Get Room User ID from DataStore
     * This is the Room database User ID (Long) stored as String
     */
    suspend fun getRoomUserIdFromDataStore(dataStore: DataStore<Preferences>): Long? {
        return try {
            val roomUserIdString = dataStore.data.map { preferences ->
                preferences[ROOM_USER_ID_KEY]
            }.first()
            roomUserIdString?.toLongOrNull()
        } catch (e: Exception) {
            android.util.Log.e("UserSessionManager", "Failed to get Room user ID from DataStore: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Get Room User ID for current user
     * This is the proper way to get the Room User ID for saving workouts
     * Returns null if no user is logged in or Room User ID not found
     */
    suspend fun getUserIdAsLong(dataStore: DataStore<Preferences>): Long? {
        // First try to get Room User ID from DataStore (fastest)
        val roomUserId = getRoomUserIdFromDataStore(dataStore)
        if (roomUserId != null) {
            return roomUserId
        }
        
        // Fallback: If Room User ID not in DataStore, return null
        // The ViewModel should create the Room User if needed
        return null
    }
}

