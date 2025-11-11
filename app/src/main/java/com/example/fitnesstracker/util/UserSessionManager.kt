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
        return dataStore.data.map { preferences ->
            preferences[USER_ID_KEY]
        }.first()
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
     * For Room database compatibility, we'll use a hash of the Firebase UID
     * This converts the Firebase UID string to a Long for the Room database
     */
    fun getUserIdAsLong(firebaseUid: String?): Long? {
        if (firebaseUid == null) return null
        // Use hashCode to convert string UID to Long
        // This is deterministic - same UID always produces same Long
        return firebaseUid.hashCode().toLong()
    }
}

