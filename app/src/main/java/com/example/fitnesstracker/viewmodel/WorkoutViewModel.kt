package com.example.fitnesstracker.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitnesstracker.data.WorkoutSession
import com.example.fitnesstracker.data.WorkoutSessionDao
import com.example.fitnesstracker.util.UserSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

class WorkoutViewModel(
    private val dao: WorkoutSessionDao,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {
    private val _selectedWorkoutType = MutableStateFlow<String?>(null)
    val selectedWorkoutType: StateFlow<String?> = _selectedWorkoutType.asStateFlow()

    private val _goalReps = MutableStateFlow(10)
    val goalReps: StateFlow<Int> = _goalReps.asStateFlow()

    private val _feedbackType = MutableStateFlow("Both")
    val feedbackType: StateFlow<String> = _feedbackType.asStateFlow()

    private val _tempo = MutableStateFlow(2.0f)
    val tempo: StateFlow<Float> = _tempo.asStateFlow()

    private val _currentSession = MutableStateFlow<WorkoutSession?>(null)
    val currentSession: StateFlow<WorkoutSession?> = _currentSession.asStateFlow()

    private val _repCount = MutableStateFlow(0)
    val repCount: StateFlow<Int> = _repCount.asStateFlow()

    // NEW: Quality tracking
    private val _goodFormReps = MutableStateFlow(0)
    val goodFormReps: StateFlow<Int> = _goodFormReps.asStateFlow()

    private val _formScore = MutableStateFlow(85f)
    val formScore: StateFlow<Float> = _formScore.asStateFlow()

    // NEW: Angle tracking for averages
    private val _avgElbowAngle = MutableStateFlow(0f)
    val avgElbowAngle: StateFlow<Float> = _avgElbowAngle.asStateFlow()

    private val _avgHipAngle = MutableStateFlow(0f)
    val avgHipAngle: StateFlow<Float> = _avgHipAngle.asStateFlow()

    private val _elbowAngles = mutableListOf<Int>()
    private val _hipAngles = mutableListOf<Int>()

    private val _isWorkoutActive = MutableStateFlow(false)
    val isWorkoutActive: StateFlow<Boolean> = _isWorkoutActive.asStateFlow()

    private val _workoutStartTime = MutableStateFlow<Long?>(null)
    val workoutStartTime: StateFlow<Long?> = _workoutStartTime.asStateFlow()

    fun selectWorkoutType(workoutType: String) {
        _selectedWorkoutType.value = workoutType
    }

    fun setGoalReps(reps: Int) {
        _goalReps.value = reps
    }

    fun setFeedbackType(type: String) {
        _feedbackType.value = type
    }

    fun setTempo(tempo: Float) {
        _tempo.value = tempo
    }

    fun startWorkout() {
        _isWorkoutActive.value = true
        _workoutStartTime.value = System.currentTimeMillis()
        _repCount.value = 0
        _goodFormReps.value = 0
        _elbowAngles.clear()
        _hipAngles.clear()
        _avgElbowAngle.value = 0f
        _avgHipAngle.value = 0f
    }

    fun incrementRep() {
        _repCount.value = _repCount.value + 1
    }

    // NEW: Update both total and good form reps
    fun updateReps(totalReps: Int, goodReps: Int) {
        _repCount.value = totalReps
        _goodFormReps.value = goodReps
    }

    // NEW: Track angles for averaging
    fun updateAngles(elbowAngle: Int, hipAngle: Int) {
        _elbowAngles.add(elbowAngle)
        _hipAngles.add(hipAngle)

        if (_elbowAngles.isNotEmpty()) {
            _avgElbowAngle.value = _elbowAngles.average().toFloat()
        }
        if (_hipAngles.isNotEmpty()) {
            _avgHipAngle.value = _hipAngles.average().toFloat()
        }
    }

    fun updateFormScore(score: Float) {
        _formScore.value = score
    }

    fun stopWorkout() {
        _isWorkoutActive.value = false
        val startTime = _workoutStartTime.value ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - startTime
        val reps = _repCount.value
        val goodReps = _goodFormReps.value
        
        // Calculate quality percentage
        val goodFormPercentage = if (reps > 0) {
            (goodReps.toFloat() / reps) * 100
        } else {
            0f
        }
        
        viewModelScope.launch {
            try {
                // Get current user ID
                val firebaseUid = UserSessionManager.getCurrentUserId(dataStore)
                val userId = UserSessionManager.getUserIdAsLong(firebaseUid)
                
                // Only save if we have a valid user ID
                if (userId != null) {
                    val session = WorkoutSession(
                        userId = userId, // ✅ CRITICAL: Associate workout with current user
                        workoutType = _selectedWorkoutType.value ?: "Push-Ups",
                        date = Date(),
                        totalReps = reps,
                        avgTempo = if (reps > 0) duration.toFloat() / (reps * 1000) else 0f,
                        formScore = goodFormPercentage,
                        goalReps = _goalReps.value,
                        duration = duration,
                        feedbackType = _feedbackType.value,
                        // Save quality metrics
                        avgElbowAngle = _avgElbowAngle.value,
                        avgHipAngle = _avgHipAngle.value,
                        goodFormPercentage = goodFormPercentage,
                        notes = "Good form reps: $goodReps/$reps"
                    )
                    
                    _currentSession.value = session
                    dao.insertSession(session)
                } else {
                    // Create session without saving to DB (for display purposes)
                    val session = WorkoutSession(
                        userId = 0L, // Placeholder user ID
                        workoutType = _selectedWorkoutType.value ?: "Push-Ups",
                        date = Date(),
                        totalReps = reps,
                        avgTempo = if (reps > 0) duration.toFloat() / (reps * 1000) else 0f,
                        formScore = goodFormPercentage,
                        goalReps = _goalReps.value,
                        duration = duration,
                        feedbackType = _feedbackType.value,
                        avgElbowAngle = _avgElbowAngle.value,
                        avgHipAngle = _avgHipAngle.value,
                        goodFormPercentage = goodFormPercentage,
                        notes = "Good form reps: $goodReps/$reps"
                    )
                    _currentSession.value = session
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Even if saving fails, create a session for display
                val session = WorkoutSession(
                    userId = 0L,
                    workoutType = _selectedWorkoutType.value ?: "Push-Ups",
                    date = Date(),
                    totalReps = reps,
                    avgTempo = if (reps > 0) duration.toFloat() / (reps * 1000) else 0f,
                    formScore = goodFormPercentage,
                    goalReps = _goalReps.value,
                    duration = duration,
                    feedbackType = _feedbackType.value,
                    avgElbowAngle = _avgElbowAngle.value,
                    avgHipAngle = _avgHipAngle.value,
                    goodFormPercentage = goodFormPercentage,
                    notes = "Good form reps: $goodReps/$reps"
                )
                _currentSession.value = session
            }
        }
    }

    fun resetWorkout() {
        _selectedWorkoutType.value = null
        _goalReps.value = 10
        _feedbackType.value = "Both"
        _tempo.value = 2.0f
        _repCount.value = 0
        _goodFormReps.value = 0
        _formScore.value = 85f
        _isWorkoutActive.value = false
        _workoutStartTime.value = null
        _currentSession.value = null
        _elbowAngles.clear()
        _hipAngles.clear()
        _avgElbowAngle.value = 0f
        _avgHipAngle.value = 0f
    }
}


