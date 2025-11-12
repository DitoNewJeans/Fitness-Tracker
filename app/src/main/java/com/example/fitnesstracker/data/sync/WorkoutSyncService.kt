package com.example.fitnesstracker.data.sync

import com.example.fitnesstracker.data.WorkoutSession
import com.example.fitnesstracker.data.WorkoutSessionDao
import com.example.fitnesstracker.data.firebase.FirestoreRepository
import com.example.fitnesstracker.data.firebase.WorkoutSessionConverter
import com.example.fitnesstracker.util.UserSessionManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

/**
 * Service to sync workout sessions between local Room database and Firestore
 */
class WorkoutSyncService(
    private val workoutSessionDao: WorkoutSessionDao,
    private val firestoreRepository: FirestoreRepository,
    private val dataStore: DataStore<Preferences>
) {
    // Mutex to prevent concurrent sync operations
    private val syncMutex = Mutex()
    /**
     * Sync data from cloud to local (pull)
     * Downloads all workouts from Firestore and saves to Room database
     */
    suspend fun syncFromCloud(): Result<Int> {
        return syncMutex.withLock {
            try {
                val firebaseUid = UserSessionManager.getCurrentUserId(dataStore)
                
                if (firebaseUid == null) {
                    return@withLock Result.failure(Exception("User not logged in"))
                }
                
                // Wait for Room User to be created (might take a moment after login)
                var roomUserId = UserSessionManager.getUserIdAsLong(dataStore)
                var retries = 0
                while (roomUserId == null && retries < 10) {
                    kotlinx.coroutines.delay(500) // Wait 500ms
                    roomUserId = UserSessionManager.getUserIdAsLong(dataStore)
                    retries++
                }
                
                if (roomUserId == null) {
                    return@withLock Result.failure(Exception("Room User not found. Please try logging in again."))
                }
                
                // Get all sessions from Firestore (using first() on Flow)
                val firestoreSessionsFlow = firestoreRepository.getWorkoutSessionsByUser(firebaseUid)
                val firestoreSessions = firestoreSessionsFlow.first()
                
                // Get existing local sessions to avoid duplicates
                val localSessionsFlow = workoutSessionDao.getSessionsByUser(roomUserId)
                val localSessions = localSessionsFlow.first()
                
                // Create a set for O(1) duplicate checking
                // Use date (rounded to minute), reps, and workout type as unique identifier
                val localSessionKeys = localSessions.map { session ->
                    // Round date to nearest minute to handle small timestamp differences
                    val roundedDate = (session.date.time / 60000) * 60000
                    "${roundedDate}_${session.totalReps}_${session.workoutType}"
                }.toSet()
                
                // Convert and save new sessions from cloud
                var syncedCount = 0
                firestoreSessions.forEach { firestoreSession ->
                    val sessionDate = firestoreSession.date.toDate().time
                    val roundedDate = (sessionDate / 60000) * 60000
                    val sessionKey = "${roundedDate}_${firestoreSession.totalReps}_${firestoreSession.workoutType}"
                    
                    // Only add if not already in local database (O(1) lookup)
                    if (!localSessionKeys.contains(sessionKey)) {
                        val roomSession = WorkoutSessionConverter.toRoom(firestoreSession, roomUserId)
                        workoutSessionDao.insertSession(roomSession)
                        syncedCount++
                    }
                }
                
                Result.success(syncedCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Sync data from local to cloud (push)
     * Uploads local workouts that don't exist in Firestore
     */
    suspend fun syncToCloud(): Result<Int> {
        return syncMutex.withLock {
            try {
                val firebaseUid = UserSessionManager.getCurrentUserId(dataStore)
                
                if (firebaseUid == null) {
                    return@withLock Result.failure(Exception("User not logged in"))
                }
                
                // Wait for Room User to be created (might take a moment after login)
                var roomUserId = UserSessionManager.getUserIdAsLong(dataStore)
                var retries = 0
                while (roomUserId == null && retries < 10) {
                    kotlinx.coroutines.delay(500) // Wait 500ms
                    roomUserId = UserSessionManager.getUserIdAsLong(dataStore)
                    retries++
                }
                
                if (roomUserId == null) {
                    return@withLock Result.failure(Exception("Room User not found. Please try logging in again."))
                }
                
                // Get all local sessions
                val localSessionsFlow = workoutSessionDao.getSessionsByUser(roomUserId)
                val localSessions = localSessionsFlow.first()
                
                // Get all cloud sessions
                val cloudSessionsFlow = firestoreRepository.getWorkoutSessionsByUser(firebaseUid)
                val cloudSessions = cloudSessionsFlow.first()
                
                // Create a set for O(1) duplicate checking
                val cloudSessionKeys = cloudSessions.map { session ->
                    val roundedDate = (session.date.toDate().time / 60000) * 60000
                    "${roundedDate}_${session.totalReps}_${session.workoutType}"
                }.toSet()
                
                // Upload local sessions that don't exist in cloud
                var syncedCount = 0
                localSessions.forEach { localSession ->
                    val roundedDate = (localSession.date.time / 60000) * 60000
                    val sessionKey = "${roundedDate}_${localSession.totalReps}_${localSession.workoutType}"
                    
                    // Check if this session already exists in cloud (O(1) lookup)
                    if (!cloudSessionKeys.contains(sessionKey)) {
                        val firestoreSession = WorkoutSessionConverter.toFirestore(localSession, firebaseUid)
                        firestoreRepository.createWorkoutSession(firestoreSession).fold(
                            onSuccess = { syncedCount++ },
                            onFailure = { /* Ignore individual failures */ }
                        )
                    }
                }
                
                Result.success(syncedCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Full sync: pull from cloud, then push local changes
     */
    suspend fun fullSync(): Result<Pair<Int, Int>> {
        return syncMutex.withLock {
            try {
                // First pull from cloud
                val pullResult = syncFromCloud()
                val pulledCount = pullResult.getOrNull() ?: 0
                
                // Then push local changes
                val pushResult = syncToCloud()
                val pushedCount = pushResult.getOrNull() ?: 0
                
                Result.success(Pair(pulledCount, pushedCount))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

