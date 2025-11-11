package com.example.fitnesstracker.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavController
import com.example.fitnesstracker.navigation.NavRoutes
import kotlinx.coroutines.delay

@Composable
fun CountdownScreen(navController: NavController) {
    var countdown by remember { mutableStateOf(3) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        delay(500)
        navController.navigate(NavRoutes.PushUpCounter.route) {
            popUpTo(NavRoutes.Home.route)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (countdown > 0) 1.5f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = ""
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (countdown > 0) {
            Text(
                text = countdown.toString(),
                fontSize = 120.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            )
        }
    }
}
