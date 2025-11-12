package com.example.fitnesstracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.fitnesstracker.data.FitnessRepository
import com.example.fitnesstracker.data.firebase.FirebaseAuthService
import com.example.fitnesstracker.data.firebase.FirestoreRepository

class FirebaseViewModelFactory(
    private val authService: FirebaseAuthService,
    private val firestoreRepository: FirestoreRepository,
    private val fitnessRepository: FitnessRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(FirebaseAuthViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                FirebaseAuthViewModel(authService, firestoreRepository, fitnessRepository, dataStore) as T
            }
            modelClass.isAssignableFrom(FirebaseWorkoutViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                FirebaseWorkoutViewModel(authService, firestoreRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}


