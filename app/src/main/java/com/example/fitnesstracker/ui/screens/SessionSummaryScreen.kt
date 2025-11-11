package com.example.fitnesstracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.fitnesstracker.navigation.NavRoutes
import com.example.fitnesstracker.viewmodel.WorkoutViewModel

@Composable
fun SessionSummaryScreen(navController: NavController, viewModel: WorkoutViewModel) {
    // Get real data from ViewModel
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    
    // Debug logging
    LaunchedEffect(currentSession) {
        println("SessionSummary - Received session: $currentSession")
    }
    
    val totalReps = currentSession?.totalReps ?: 0
    val goodFormPercentage = currentSession?.goodFormPercentage ?: 0f
    val avgElbowAngle = currentSession?.avgElbowAngle ?: 0f
    val avgHipAngle = currentSession?.avgHipAngle ?: 0f
    val duration = currentSession?.duration ?: 0L
    val notes = currentSession?.notes ?: ""
    val avgTempo = if (totalReps > 0) (duration.toFloat() / (totalReps * 1000)) else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Success icon
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Workout Complete!",
            style = MaterialTheme.typography.headlineLarge
        )
        
        Text(
            text = "Great job! Here's your summary",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))
        
        // Debug warning if no data
        if (totalReps == 0 && currentSession == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("⚠️ No workout data received", color = MaterialTheme.colorScheme.error)
                    Text("Session: $currentSession", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Total reps card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total Reps",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = totalReps.toString(),
                    style = MaterialTheme.typography.displayLarge
                )
            }
        }

        // Form quality card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Form Quality",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${goodFormPercentage.toInt()}%",
                    style = MaterialTheme.typography.displayLarge,
                    color = when {
                        goodFormPercentage >= 80f -> androidx.compose.ui.graphics.Color(0xFF00FF6D)
                        goodFormPercentage >= 60f -> androidx.compose.ui.graphics.Color(0xFFFFA500)
                        else -> androidx.compose.ui.graphics.Color(0xFFFF6B6B)
                    }
                )
                if (notes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Technical details card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Technical Details",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DetailItem("Avg Elbow", "${avgElbowAngle.toInt()}°")
                    DetailItem("Avg Hip", "${avgHipAngle.toInt()}°")
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DetailItem("Duration", "${duration/1000}s")
                    DetailItem("Avg Tempo", "${String.format("%.1f", avgTempo)}s/rep")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons (stacked, not side-by-side)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    viewModel.resetWorkout()
                    navController.navigate(NavRoutes.Home.route) {
                        popUpTo(NavRoutes.Home.route)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Back to Home", style = MaterialTheme.typography.titleMedium)
            }

            OutlinedButton(
                onClick = {
                    navController.navigate(NavRoutes.History.route) {
                        popUpTo(NavRoutes.Home.route)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("View History")
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}
