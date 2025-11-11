package com.example.fitnesstracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.fitnesstracker.data.AppDatabase
import com.example.fitnesstracker.navigation.NavRoutes
import kotlinx.coroutines.flow.map
import kotlin.system.exitProcess

@Composable
fun HomeScreen(navController: NavController) {
    // Pressing back on Home screen exits the app
    BackHandler {
        exitProcess(0)
    }
    
    // Get real stats from database for CURRENT USER only
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    
    // Get current user ID
    val userId by remember {
        kotlinx.coroutines.flow.flow {
            val firebaseUid = com.example.fitnesstracker.util.UserSessionManager.getCurrentUserId(context.dataStore)
            emit(com.example.fitnesstracker.util.UserSessionManager.getUserIdAsLong(firebaseUid))
        }
    }.collectAsStateWithLifecycle(initialValue = null)
    
    // Filter stats by current user
    val workoutCount by remember(userId) {
        if (userId != null) {
            database.workoutSessionDao().getSessionsByUser(userId!!).map { it.size }
        } else {
            kotlinx.coroutines.flow.flowOf(0)
        }
    }.collectAsStateWithLifecycle(initialValue = 0)
    
    val totalReps by remember(userId) {
        if (userId != null) {
            database.workoutSessionDao().getSessionsByUser(userId!!).map { sessions ->
                sessions.sumOf { it.totalReps }
            }
        } else {
            kotlinx.coroutines.flow.flowOf(0)
        }
    }.collectAsStateWithLifecycle(initialValue = 0)
    
    val avgFormScore by remember(userId) {
        if (userId != null) {
            database.workoutSessionDao().getSessionsByUser(userId!!).map { sessions ->
                if (sessions.isEmpty()) 0f
                else sessions.map { it.goodFormPercentage ?: it.formScore }.average().toFloat()
            }
        } else {
            kotlinx.coroutines.flow.flowOf(0f)
        }
    }.collectAsStateWithLifecycle(initialValue = 0f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 24.dp)
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ready to crush your goals?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Quick Stats Card (Real data from database)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem(workoutCount.toString(), "Workouts")
                Divider(
                    modifier = Modifier
                        .height(40.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                )
                StatItem(totalReps.toString(), "Reps")
                Divider(
                    modifier = Modifier
                        .height(40.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                )
                StatItem("${avgFormScore.toInt()}%", "Form")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                title = "Start Workout",
                subtitle = "Begin your training session",
                icon = Icons.Default.PlayArrow,
                onClick = { navController.navigate(NavRoutes.SelectWorkout.route) }
            )

            ActionCard(
                title = "History & Stats",
                subtitle = "View your progress",
                icon = Icons.Default.List,
                onClick = { navController.navigate(NavRoutes.History.route) }
            )

            ActionCard(
                title = "Settings",
                subtitle = "Manage your account",
                icon = Icons.Default.Settings,
                onClick = { navController.navigate(NavRoutes.Settings.route) }
            )
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Go",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
