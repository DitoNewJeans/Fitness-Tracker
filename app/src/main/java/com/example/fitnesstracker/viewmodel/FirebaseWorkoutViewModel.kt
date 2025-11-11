package com.example.fitnesstracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitnesstracker.data.firebase.FirebaseAuthService
import com.example.fitnesstracker.data.firebase.FirestoreRepository
import com.example.fitnesstracker.data.firebase.FirestoreWorkoutSession
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FirebaseWorkoutViewModel(
    private val authService: FirebaseAuthService,
    private val firestoreRepository: FirestoreRepository
) : ViewModel() {
    private val _selectedWorkoutType = MutableStateFlow<String?>(null)
    val selectedWorkoutType: StateFlow<String?> = _selectedWorkoutType.asStateFlow()

    private val _goalReps = MutableStateFlow(10)
    val goalReps: StateFlow<Int> = _goalReps.asStateFlow()

    private val _feedbackType = MutableStateFlow("Both")
    val feedbackType: StateFlow<String> = _feedbackType.asStateFlow()

    private val _tempo = MutableStateFlow(2.0f)
    val tempo: StateFlow<Float> = _tempo.asStateFlow()

    private val _currentSession = MutableStateFlow<FirestoreWorkoutSession?>(null)
    val currentSession: StateFlow<FirestoreWorkoutSession?> = _currentSession.asStateFlow()

    private val _repCount = MutableStateFlow(0)
    val repCount: StateFlow<Int> = _repCount.asStateFlow()

    private val _formScore = MutableStateFlow(85f)
    val formScore: StateFlow<Float> = _formScore.asStateFlow()

    private val _isWorkoutActive = MutableStateFlow(false)
    val isWorkoutActive: StateFlow<Boolean> = _isWorkoutActive.asStateFlow()

    private val _workoutStartTime = MutableStateFlow<Long?>(null)
    val workoutStartTime: StateFlow<Long?> = _workoutStartTime.asStateFlow()

    // User-specific workout sessions
    private val _workoutSessions = MutableStateFlow<List<FirestoreWorkoutSession>>(emptyList())
    val workoutSessions: StateFlow<List<FirestoreWorkoutSession>> = _workoutSessions.asStateFlow()

    // User stats
    private val _totalSessions = MutableStateFlow(0)
    val totalSessions: StateFlow<Int> = _totalSessions.asStateFlow()

    private val _totalReps = MutableStateFlow(0)
    val totalReps: StateFlow<Int> = _totalReps.asStateFlow()

    private val _averageFormScore = MutableStateFlow(0f)
    val averageFormScore: StateFlow<Float> = _averageFormScore.asStateFlow()

    init {
        loadUserWorkouts()
        loadUserStats()
    }

    private fun loadUserWorkouts() {
        val userId = authService.currentUser?.uid ?: return
        viewModelScope.launch {
            firestoreRepository.getWorkoutSessionsByUser(userId).collect { sessions ->
                _workoutSessions.value = sessions
            }
        }
    }

    private fun loadUserStats() {
        val userId = authService.currentUser?.uid ?: return
        viewModelScope.launch {
            // Load total sessions
            firestoreRepository.getTotalSessionsByUser(userId).fold(
                onSuccess = { count -> _totalSessions.value = count },
                onFailure = { _totalSessions.value = 0 }
            )

            // Load total reps
            firestoreRepository.getTotalRepsByUser(userId).fold(
                onSuccess = { count -> _totalReps.value = count },
                onFailure = { _totalReps.value = 0 }
            )

            // Load average form score
            firestoreRepository.getAverageFormScoreByUser(userId).fold(
                onSuccess = { score -> _averageFormScore.value = score },
                onFailure = { _averageFormScore.value = 0f }
            )
        }
    }

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
    }

    fun incrementRep() {
        _repCount.value = _repCount.value + 1
    }

    fun updateFormScore(score: Float) {
        _formScore.value = score
    }

    fun stopWorkout() {
        _isWorkoutActive.value = false
        val startTime = _workoutStartTime.value ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - startTime
        val reps = _repCount.value
        
        val userId = authService.currentUser?.uid ?: return
        
        val session = FirestoreWorkoutSession(
            userId = userId,
            workoutType = _selectedWorkoutType.value ?: "Push-Ups",
            date = Timestamp.now(),
            totalReps = reps,
            avgTempo = if (reps > 0) duration.toFloat() / (reps * 1000) else 0f,
            formScore = _formScore.value,
            goalReps = _goalReps.value,
            duration = duration,
            feedbackType = _feedbackType.value
        )
        
        _currentSession.value = session
        
        viewModelScope.launch {
            firestoreRepository.createWorkoutSession(session).fold(
                onSuccess = {
                    // Session saved successfully, reload stats
                    loadUserStats()
                },
                onFailure = { e ->
                    // Handle error (could add error state here)
                    e.printStackTrace()
                }
            )
        }
    }

    fun resetWorkout() {
        _selectedWorkoutType.value = null
        _goalReps.value = 10
        _feedbackType.value = "Both"
        _tempo.value = 2.0f
        _repCount.value = 0
        _formScore.value = 85f
        _isWorkoutActive.value = false
        _workoutStartTime.value = null
        _currentSession.value = null
    }

    fun refreshStats() {
        loadUserStats()
    }
}

