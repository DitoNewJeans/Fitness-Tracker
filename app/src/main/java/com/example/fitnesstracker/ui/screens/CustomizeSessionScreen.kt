package com.example.fitnesstracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fitnesstracker.data.AppDatabase
import com.example.fitnesstracker.navigation.NavRoutes
import com.example.fitnesstracker.viewmodel.ViewModelFactory
import com.example.fitnesstracker.viewmodel.WorkoutViewModel
import com.example.fitnesstracker.dataStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeSessionScreen(navController: NavController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val workoutViewModel: WorkoutViewModel = viewModel(
        factory = ViewModelFactory(database.workoutSessionDao(), context.dataStore)
    )
    
    var goalReps by remember { mutableStateOf(10) }
    var feedbackType by remember { mutableStateOf("Both") }
    var tempo by remember { mutableStateOf(2.0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customize Session") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Goal Reps: $goalReps", style = MaterialTheme.typography.titleLarge)
            Slider(
                value = goalReps.toFloat(),
                onValueChange = { goalReps = it.toInt() },
                valueRange = 1f..100f,
                steps = 98
            )

            Text("Feedback Type: $feedbackType", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Voice", "Visual", "Both").forEach { type ->
                    FilterChip(
                        selected = feedbackType == type,
                        onClick = { feedbackType = type },
                        label = { Text(type) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text("Tempo: ${String.format("%.1f", tempo)}s per rep", style = MaterialTheme.typography.titleLarge)
            Slider(
                value = tempo,
                onValueChange = { tempo = it },
                valueRange = 1f..5f,
                steps = 39
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    // Save settings to ViewModel
                    workoutViewModel.setGoalReps(goalReps)
                    workoutViewModel.setFeedbackType(feedbackType)
                    workoutViewModel.setTempo(tempo)
                    
                    navController.navigate(NavRoutes.Countdown.route)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Ready to Start")
            }
        }
    }
}
