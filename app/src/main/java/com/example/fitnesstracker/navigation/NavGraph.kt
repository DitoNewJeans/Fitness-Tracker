package com.example.fitnesstracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.fitnesstracker.data.AppDatabase
import com.example.fitnesstracker.data.firebase.FirebaseAuthService
import com.example.fitnesstracker.data.firebase.FirestoreRepository
import com.example.fitnesstracker.ui.screens.*
import com.example.fitnesstracker.viewmodel.FirebaseAuthViewModel
import com.example.fitnesstracker.viewmodel.FirebaseViewModelFactory
import com.example.fitnesstracker.viewmodel.ViewModelFactory
import com.example.fitnesstracker.viewmodel.WorkoutViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = NavRoutes.Welcome.route,
    authService: FirebaseAuthService,
    firestoreRepository: FirestoreRepository,
    dataStore: DataStore<Preferences>
) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    
    val firebaseViewModelFactory = FirebaseViewModelFactory(authService, firestoreRepository, dataStore)
    val workoutViewModelFactory = ViewModelFactory(database.workoutSessionDao(), dataStore)
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.Welcome.route) {
            WelcomeScreen(navController = navController)
        }
        composable(NavRoutes.Login.route) {
            val authViewModel: FirebaseAuthViewModel = viewModel(
                factory = firebaseViewModelFactory
            )
            LoginScreen(navController = navController, authViewModel = authViewModel)
        }
        composable(NavRoutes.CreateAccount.route) {
            val authViewModel: FirebaseAuthViewModel = viewModel(
                factory = firebaseViewModelFactory
            )
            CreateAccountScreen(navController = navController, authViewModel = authViewModel)
        }
        composable(NavRoutes.AppIntroduction.route) {
            AppIntroductionScreen(navController = navController)
        }
        composable(NavRoutes.PermissionsRequest.route) {
            PermissionsRequestScreen(navController = navController)
        }
        composable(NavRoutes.Tutorial.route) {
            TutorialScreen(navController = navController)
        }
        composable(NavRoutes.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(NavRoutes.SelectWorkout.route) {
            SelectWorkoutScreen(navController = navController)
        }
        composable(NavRoutes.CustomizeSession.route) {
            CustomizeSessionScreen(navController = navController)
        }
        composable(NavRoutes.Countdown.route) {
            CountdownScreen(navController = navController)
        }
        composable(NavRoutes.WorkoutSession.route) {
            WorkoutSessionScreen(navController = navController)
        }
        composable(NavRoutes.PushUpCounter.route) {
            // CRITICAL: Scope to Home.route to share ViewModel with SessionSummary
            // Try to get Home back stack entry, fallback to current entry if not available
            val workoutViewModel: WorkoutViewModel = viewModel(
                viewModelStoreOwner = remember { 
                    try {
                        navController.getBackStackEntry(NavRoutes.Home.route)
                    } catch (e: IllegalArgumentException) {
                        // Fallback: if Home is not in back stack, use current entry
                        navController.currentBackStackEntry!!
                    }
                },
                factory = workoutViewModelFactory
            )
            PushUpCounterScreen(navController = navController, viewModel = workoutViewModel)
        }
        composable(NavRoutes.SessionSummary.route) {
            // CRITICAL: Scope to Home.route to get SAME ViewModel instance
            // Try to get Home back stack entry, fallback to current entry if not available
            val workoutViewModel: WorkoutViewModel = viewModel(
                viewModelStoreOwner = remember { 
                    try {
                        navController.getBackStackEntry(NavRoutes.Home.route)
                    } catch (e: IllegalArgumentException) {
                        // Fallback: if Home is not in back stack, use current entry
                        navController.currentBackStackEntry!!
                    }
                },
                factory = workoutViewModelFactory
            )
            SessionSummaryScreen(navController = navController, viewModel = workoutViewModel)
        }
        composable(NavRoutes.History.route) {
            HistoryScreen(navController = navController)
        }
        composable("${NavRoutes.SessionDetails.route}/{sessionId}") { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId")?.toLongOrNull()
            SessionDetailsScreen(
                navController = navController,
                sessionId = sessionId ?: 0L
            )
        }
        composable(NavRoutes.Settings.route) {
            val authViewModel: FirebaseAuthViewModel = viewModel(
                factory = firebaseViewModelFactory
            )
            SettingsScreen(navController = navController, authViewModel = authViewModel)
        }
        composable(NavRoutes.Profile.route) {
            val authViewModel: FirebaseAuthViewModel = viewModel(
                factory = firebaseViewModelFactory
            )
            ProfileScreen(
                navController = navController,
                authViewModel = authViewModel,
                viewModelFactory = firebaseViewModelFactory
            )
        }
        composable(NavRoutes.AppPreferences.route) {
            AppPreferencesScreen(navController = navController)
        }
        composable(NavRoutes.TutorialHelp.route) {
            TutorialHelpScreen(navController = navController)
        }
        composable(NavRoutes.PairWatch.route) {
            PairWatchScreen(navController = navController)
        }
    }
}


