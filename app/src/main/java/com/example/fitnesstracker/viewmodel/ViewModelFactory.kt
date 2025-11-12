package com.example.fitnesstracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.fitnesstracker.data.WorkoutSessionDao
import com.example.fitnesstracker.data.firebase.FirestoreRepository

/**
 * ViewModelFactory for WorkoutViewModel
 * Note: AuthViewModel uses FirebaseViewModelFactory separately
 */
class ViewModelFactory(
    private val workoutSessionDao: WorkoutSessionDao,
    private val firestoreRepository: FirestoreRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkoutViewModel(workoutSessionDao, firestoreRepository, dataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}



