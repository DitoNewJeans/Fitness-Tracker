package com.example.fitnesstracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fitnesstracker.dataStore
import com.example.fitnesstracker.util.ThemeManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPreferencesScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Read dark mode preference from DataStore
    val darkModeEnabled by ThemeManager.getDarkModePreference(context.dataStore)
        .collectAsState(initial = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Preferences") },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Dark Mode")
                Switch(
                    checked = darkModeEnabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            ThemeManager.setDarkModePreference(context.dataStore, enabled)
                        }
                    }
                )
            }
        }
    }
}
