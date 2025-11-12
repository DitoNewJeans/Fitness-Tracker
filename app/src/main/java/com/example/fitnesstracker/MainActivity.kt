package com.example.fitnesstracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.fitnesstracker.data.firebase.FirebaseAuthService
import com.example.fitnesstracker.data.firebase.FirestoreRepository
import com.example.fitnesstracker.navigation.NavGraph
import com.example.fitnesstracker.navigation.NavRoutes
import com.example.fitnesstracker.ui.theme.FitnessTrackerTheme
import com.example.fitnesstracker.util.ThemeManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Firebase services
        val authService = FirebaseAuthService()
        val firestoreRepository = FirestoreRepository()
        
        setContent {
            // Read dark mode preference from DataStore (defaults to true for dark mode)
            val darkModePreference by ThemeManager.getDarkModePreference(dataStore)
                .collectAsState(initial = true)
            
            FitnessTrackerTheme(darkTheme = darkModePreference) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    // Firebase Auth automatically restores user session
                    // Check if user is logged in to determine start destination
                    val startDestination = if (authService.isUserLoggedIn) {
                        NavRoutes.Home.route
                    } else {
                        NavRoutes.Welcome.route
                    }
                    
                    NavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        authService = authService,
                        firestoreRepository = firestoreRepository,
                        dataStore = dataStore
                    )
                }
            }
        }
    }
}