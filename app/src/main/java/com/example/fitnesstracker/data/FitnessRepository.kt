package com.example.fitnesstracker.data

import kotlinx.coroutines.flow.Flow
import java.util.Date

class FitnessRepository(
    private val userDao: UserDao,
    private val exerciseDao: ExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao
) {
    // User operations
    fun getAllUsers(): Flow<List<User>> = userDao.getAllUsers()
    suspend fun getUserById(userId: Long): User? = userDao.getUserById(userId)
    suspend fun getUserByEmail(email: String): User? = userDao.getUserByEmail(email)
    suspend fun getUserByFirebaseUid(firebaseUid: String): User? = userDao.getUserByFirebaseUid(firebaseUid)
    suspend fun insertUser(user: User): Long = userDao.insertUser(user)
    
    /**
     * Get or create a Room User from Firebase UID
     * This ensures each Firebase user has a corresponding Room User record
     */
    suspend fun getOrCreateUserFromFirebaseUid(
        firebaseUid: String,
        name: String,
        email: String
    ): User {
        // Try to find existing user by Firebase UID
        val existingUser = getUserByFirebaseUid(firebaseUid)
        if (existingUser != null) {
            // Update last login and return existing user
            updateLastLogin(existingUser.id)
            return existingUser
        }
        
        // Create new Room User for this Firebase user
        val newUser = User(
            name = name,
            email = email,
            firebaseUid = firebaseUid,
            createdAt = Date(),
            lastLoginAt = Date()
        )
        val userId = insertUser(newUser)
        return newUser.copy(id = userId)
    }
    suspend fun updateUser(user: User) = userDao.updateUser(user)
    suspend fun deleteUser(user: User) = userDao.deleteUser(user)
    suspend fun updateLastLogin(userId: Long, loginTime: Date = Date()) = 
        userDao.updateLastLogin(userId, loginTime)

    // Exercise operations
    fun getAllExercises(): Flow<List<Exercise>> = exerciseDao.getAllExercises()
    suspend fun getExerciseById(exerciseId: Long): Exercise? = exerciseDao.getExerciseById(exerciseId)
    suspend fun getExerciseByName(name: String): Exercise? = exerciseDao.getExerciseByName(name)
    suspend fun insertExercise(exercise: Exercise): Long = exerciseDao.insertExercise(exercise)
    suspend fun insertExercises(exercises: List<Exercise>) = exerciseDao.insertExercises(exercises)
    suspend fun updateExercise(exercise: Exercise) = exerciseDao.updateExercise(exercise)
    suspend fun deleteExercise(exercise: Exercise) = exerciseDao.deleteExercise(exercise)

    // Workout Session operations
    fun getAllSessions(): Flow<List<WorkoutSession>> = workoutSessionDao.getAllSessions()
    fun getSessionsByUser(userId: Long): Flow<List<WorkoutSession>> = 
        workoutSessionDao.getSessionsByUser(userId)
    fun getSessionsByExercise(exerciseId: Long): Flow<List<WorkoutSession>> = 
        workoutSessionDao.getSessionsByExercise(exerciseId)
    fun getSessionsByWorkoutType(workoutType: String): Flow<List<WorkoutSession>> = 
        workoutSessionDao.getSessionsByWorkoutType(workoutType)
    suspend fun getSessionById(sessionId: Long): WorkoutSession? = 
        workoutSessionDao.getSessionById(sessionId)
    suspend fun insertSession(session: WorkoutSession): Long = workoutSessionDao.insertSession(session)
    suspend fun updateSession(session: WorkoutSession) = workoutSessionDao.updateSession(session)
    suspend fun deleteSession(session: WorkoutSession) = workoutSessionDao.deleteSession(session)
    suspend fun deleteSessionById(sessionId: Long) = workoutSessionDao.deleteSessionById(sessionId)
    
    // Statistics
    suspend fun getTotalSessions(): Int = workoutSessionDao.getTotalSessions()
    suspend fun getTotalSessionsByUser(userId: Long): Int = 
        workoutSessionDao.getTotalSessionsByUser(userId)
    suspend fun getTotalReps(): Int? = workoutSessionDao.getTotalReps()
    suspend fun getTotalRepsByUser(userId: Long): Int? = workoutSessionDao.getTotalRepsByUser(userId)
    suspend fun getAverageFormScoreByUser(userId: Long): Float? = 
        workoutSessionDao.getAverageFormScoreByUser(userId)
    fun getSessionsByDateRange(startDate: Date, endDate: Date): Flow<List<WorkoutSession>> = 
        workoutSessionDao.getSessionsByDateRange(startDate, endDate)

    // Initialize default exercises if they don't exist
    suspend fun initializeDefaultExercises() {
        // Try to get an exercise by name to check if any exist
        val pushUpExercise = exerciseDao.getExerciseByName("Push-Ups")
        
        if (pushUpExercise == null) {
            val defaultExercises = listOf(
                Exercise(
                    name = "Push-Ups",
                    description = "Upper body strength exercise targeting chest, triceps, and shoulders",
                    muscleGroups = "Chest, Triceps, Shoulders",
                    difficulty = "Beginner",
                    isActive = true
                ),
                Exercise(
                    name = "Squats",
                    description = "Lower body strength exercise targeting quadriceps, glutes, and hamstrings",
                    muscleGroups = "Quadriceps, Glutes, Hamstrings",
                    difficulty = "Beginner",
                    isActive = true
                ),
                Exercise(
                    name = "Pull-Ups",
                    description = "Upper body pulling exercise targeting back and biceps",
                    muscleGroups = "Back, Biceps",
                    difficulty = "Intermediate",
                    isActive = true
                ),
                Exercise(
                    name = "Planks",
                    description = "Core strength exercise targeting core and shoulders",
                    muscleGroups = "Core, Shoulders",
                    difficulty = "Beginner",
                    isActive = true
                )
            )
            exerciseDao.insertExercises(defaultExercises)
        }
    }
}

