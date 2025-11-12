package com.example.fitnesstracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.fitnesstracker.dataStore
import com.example.fitnesstracker.data.AppDatabase
import com.example.fitnesstracker.data.WorkoutSession
import com.example.fitnesstracker.data.firebase.FirestoreRepository
import com.example.fitnesstracker.data.sync.WorkoutSyncService
import com.example.fitnesstracker.navigation.NavRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val firestoreRepository = remember { FirestoreRepository() }
    val syncService = remember {
        WorkoutSyncService(
            database.workoutSessionDao(),
            firestoreRepository,
            context.dataStore
        )
    }
    
    // Trigger sync when screen is opened
    LaunchedEffect(Unit) {
        // Sync from cloud when History screen opens
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val syncResult = syncService.syncFromCloud()
                syncResult.fold(
                    onSuccess = { count ->
                        android.util.Log.d("HistorySync", "Synced $count workouts from cloud")
                    },
                    onFailure = { e ->
                        android.util.Log.e("HistorySync", "Sync failed: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("HistorySync", "Sync error: ${e.message}")
            }
        }
    }
    
    // Get current Room User ID from DataStore
    val userId by remember {
        kotlinx.coroutines.flow.flow {
            val id = com.example.fitnesstracker.util.UserSessionManager.getUserIdAsLong(context.dataStore)
            android.util.Log.d("HistoryScreen", "Current userId: $id")
            emit(id)
        }
    }.collectAsStateWithLifecycle(initialValue = null)
    
    // Get workout sessions for CURRENT USER only
    val sessionsFlow: Flow<List<WorkoutSession>> = remember(userId) {
        if (userId != null) {
            android.util.Log.d("HistoryScreen", "Querying sessions for userId: $userId")
            database.workoutSessionDao().getSessionsByUser(userId!!)
        } else {
            android.util.Log.d("HistoryScreen", "userId is null, returning empty list")
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    
    // Log when sessions change
    LaunchedEffect(sessions.size) {
        android.util.Log.d("HistoryScreen", "Sessions count: ${sessions.size}")
    }

    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    fun refreshData() {
        isRefreshing = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val syncResult = syncService.syncFromCloud()
                syncResult.fold(
                    onSuccess = { count ->
                        android.util.Log.d("HistorySync", "Manual sync: $count workouts synced")
                    },
                    onFailure = { e ->
                        android.util.Log.e("HistorySync", "Manual sync failed: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("HistorySync", "Manual sync error: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History & Stats") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshData() },
                        enabled = !isRefreshing
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (sessions.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Text(
                        "No workouts yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Complete a workout to see your history",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { navController.navigate(NavRoutes.SelectWorkout.route) }) {
                        Text("Start Workout")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sessions.sortedByDescending { it.date }) { session ->
                    WorkoutSessionCard(session, navController)
                }
            }
        }
    }
}

@Composable
private fun WorkoutSessionCard(session: WorkoutSession, navController: NavController) {
    Card(
        onClick = {
            navController.navigate("${NavRoutes.SessionDetails.route}/${session.id}")
        },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.workoutType,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                            .format(session.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(
                        text = "${session.totalReps} reps",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Quality metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val formScore = session.goodFormPercentage ?: session.formScore
                QualityMetric(
                    label = "Form Quality",
                    value = "${formScore.toInt()}%",
                    color = when {
                        formScore >= 80f -> androidx.compose.ui.graphics.Color(0xFF00FF6D)
                        formScore >= 60f -> androidx.compose.ui.graphics.Color(0xFFFFA500)
                        else -> androidx.compose.ui.graphics.Color(0xFFFF6B6B)
                    }
                )
                
                session.avgElbowAngle?.let { angle ->
                    QualityMetric(
                        label = "Avg Elbow",
                        value = "${angle.toInt()}°"
                    )
                }
                
                QualityMetric(
                    label = "Duration",
                    value = "${session.duration / 1000}s"
                )
            }
            
            // Notes if available
            session.notes?.let { notes ->
                if (notes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityMetric(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
