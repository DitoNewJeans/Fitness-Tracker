package com.example.fitnesstracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fitnesstracker.navigation.NavRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectWorkoutScreen(navController: NavController) {
    val workouts = listOf("Push-Ups")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Workout") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            workouts.forEach { workout ->
                Card(
                    onClick = {
                        if (workout == "Push-Ups") {
                            navController.navigate(NavRoutes.PushUpCounter.route)
                        } else {
                            navController.navigate(NavRoutes.CustomizeSession.route)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = workout,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
            }
        }
    }
}
