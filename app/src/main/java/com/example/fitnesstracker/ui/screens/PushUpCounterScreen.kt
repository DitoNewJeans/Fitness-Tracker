package com.example.fitnesstracker.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Size as AndroidSize
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.PoseDetection
import kotlinx.coroutines.asExecutor
import com.example.fitnesstracker.domain.PoseRepCounter
import com.example.fitnesstracker.domain.PoseRepCounter.Companion.P
import com.example.fitnesstracker.domain.PoseRepCounter.Companion.calculateAngleDeg
import com.example.fitnesstracker.domain.RepQuality
import com.example.fitnesstracker.viewmodel.WorkoutViewModel
import com.example.fitnesstracker.navigation.NavRoutes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.PathEffect

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PushUpCounterScreen(navController: NavController, viewModel: WorkoutViewModel) {
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        } else {
            // Start workout when screen loads
            viewModel.selectWorkoutType("Push-Ups")
            viewModel.startWorkout()
        }
    }

    if (!cameraPermission.status.isGranted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission required", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                    Text("Grant camera access")
                }
            }
        }
        return
    }

    PushUpCounterContent(navController, viewModel)
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun PushUpCounterContent(navController: NavController, viewModel: WorkoutViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var reps by remember { mutableStateOf(0) }
    var goodFormReps by remember { mutableStateOf(0) }
    var stage by remember { mutableStateOf<String?>(null) }
    var elbowDeg by remember { mutableStateOf(0) }
    var hipDeg by remember { mutableStateOf(0) }
    var formFeedback by remember { mutableStateOf("") }
    var repQuality by remember { mutableStateOf(RepQuality.ACCEPTABLE) }
    var landmarkPoints by remember { mutableStateOf<Map<Int, Pair<Float, Float>>>(emptyMap()) }
    var imageWidth by remember { mutableStateOf(0) }
    var imageHeight by remember { mutableStateOf(0) }
    var imageRotation by remember { mutableStateOf(0) }
    var showFinishDialog by remember { mutableStateOf(false) }
    
    // Workout timer
    val goalReps by viewModel.goalReps.collectAsState()
    val workoutStartTime by viewModel.workoutStartTime.collectAsState()
    var elapsedTime by remember { mutableStateOf(0L) }
    
    // Timer update - runs continuously while workout is active
    LaunchedEffect(workoutStartTime) {
        val startTime = workoutStartTime
        if (startTime != null) {
            // Update immediately when workout starts
            elapsedTime = System.currentTimeMillis() - startTime
            
            // Timer loop - updates every second
            while (true) {
                delay(1000)
                val currentStartTime = viewModel.workoutStartTime.value
                if (currentStartTime != null) {
                    elapsedTime = System.currentTimeMillis() - currentStartTime
                } else {
                    elapsedTime = 0L
                    break // Stop if workoutStartTime becomes null
                }
            }
        } else {
            elapsedTime = 0L // Reset if no workout active
        }
    }
    
    // Format time as MM:SS
    val formattedTime = remember(elapsedTime) {
        val seconds = (elapsedTime / 1000).toInt()
        val minutes = seconds / 60
        val secs = seconds % 60
        String.format("%02d:%02d", minutes, secs)
    }
    
    // Rep counter animation
    val previousReps = remember { mutableStateOf(0) }
    val scale by animateFloatAsState(
        targetValue = if (reps != previousReps.value) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = { previousReps.value = reps }
    )

    // Pose detector
    val options = remember {
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
    }
    val detector = remember { PoseDetection.getClient(options) }
    val repCounter = remember { PoseRepCounter() }

    // PreviewView for CameraX
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val pv = PreviewView(ctx).apply {
                    setBackgroundColor(Color.BLACK)
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                previewView = pv

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .build().also { it.setSurfaceProvider(pv.surfaceProvider) }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(AndroidSize(720, 1280))
                        .setTargetRotation(pv.display.rotation)
                        .build()

                    analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                        processFrame(
                            detector,
                            imageProxy,
                            onAngles = { eDeg: Int, hDeg: Int ->
                                val state = repCounter.update(eDeg, hDeg)
                                elbowDeg = state.elbowDeg
                                hipDeg = state.hipDeg
                                reps = state.reps
                                goodFormReps = state.goodFormReps
                                stage = state.stage
                                formFeedback = state.formFeedback
                                repQuality = state.repQuality
                                
                                // Update ViewModel
                                viewModel.updateReps(reps, goodFormReps)
                                viewModel.updateAngles(eDeg, hDeg)
                            },
                            onRep = { _, _ -> },
                            onLandmarks = { lm ->
                                landmarkPoints = lm
                            },
                            onImageInfo = { iw, ih, rot ->
                                imageWidth = iw
                                imageHeight = ih
                                imageRotation = rot
                            },
                            onNoPerson = {
                                // Do not advance counter/state when no person; just zero the display
                                elbowDeg = 0
                                hipDeg = 0
                                stage = null
                                formFeedback = ""
                                landmarkPoints = emptyMap()
                                imageWidth = 0
                                imageHeight = 0
                                imageRotation = 0
                            }
                        )
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            analysis
                        )
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))

                pv
            }
        )

        // Skeleton overlay
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        ) {
            if (landmarkPoints.isNotEmpty() && imageWidth > 0 && imageHeight > 0) {
                val w = size.width
                val h = size.height

                fun mapPointFromImage(x: Float, y: Float): Offset {
                    // Landmarks are already in the rotated upright image space by ML Kit.
                    val rw = imageWidth.toFloat()
                    val rh = imageHeight.toFloat()
                    val scale = kotlin.math.max(w / rw, h / rh)
                    val dx = (w - rw * scale) / 2f
                    val dy = (h - rh * scale) / 2f
                    return Offset(dx + x * scale, dy + y * scale)
                }

                fun pt(type: Int): Offset? = landmarkPoints[type]?.let { (ix, iy) ->
                    mapPointFromImage(ix, iy)
                }

                val connections = listOf(
                    // torso
                    PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
                    PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
                    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
                    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
                    // left arm
                    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
                    PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
                    // right arm
                    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
                    PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
                    // left leg
                    PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
                    PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
                    // right leg
                    PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
                    PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE
                )

                // draw bones
                connections.forEach { (a, b) ->
                    val pa = pt(a)
                    val pb = pt(b)
                    if (pa != null && pb != null) {
                        drawLine(
                            color = UiColor(0xFF00E5FF),
                            start = pa,
                            end = pb,
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // draw joints
                landmarkPoints.values.forEach { (ix, iy) ->
                    val p = mapPointFromImage(ix, iy)
                    drawCircle(
                        color = UiColor(0xFF00FF6D),
                        radius = 6f,
                        center = p,
                        style = Stroke(width = 4f)
                    )
                }
            }
        }

        // Top Left: Rep Counter with Progress Ring
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .zIndex(1f)
        ) {
            Card(
                modifier = Modifier.width(140.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = UiColor.Black.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Circular Progress Ring with Rep Count
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(100.dp)
                    ) {
                        // Progress ring
                        CircularProgressRing(
                            progress = if (goalReps > 0) (reps.toFloat() / goalReps).coerceAtMost(1f) else 0f,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Rep count in center
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.scale(scale)
                        ) {
                            Text(
                                text = "$reps",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = UiColor.White
                            )
                            Text(
                                text = "/ $goalReps",
                                style = MaterialTheme.typography.bodySmall,
                                color = UiColor.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Good Form Count
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "✓ $goodFormReps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = UiColor(0xFF00FF6D)
                        )
                    }
                }
            }
        }
        
        // Top Right: Timer and Form Quality Gauge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .zIndex(1f)
        ) {
            Card(
                modifier = Modifier.width(140.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = UiColor.Black.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Timer
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = UiColor.White
                    )
                    Text(
                        text = "Time",
                        style = MaterialTheme.typography.bodySmall,
                        color = UiColor.White.copy(alpha = 0.7f)
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Form Quality Gauge
                    val formPercentage = if (reps > 0) (goodFormReps.toFloat() / reps * 100) else 0f
                    FormQualityGauge(
                        percentage = formPercentage,
                        modifier = Modifier.size(60.dp)
                    )
                    
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${formPercentage.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = UiColor.White
                    )
                    Text(
                        text = "Form Quality",
                        style = MaterialTheme.typography.labelSmall,
                        color = UiColor.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Bottom Left: Angle Indicators
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .zIndex(1f)
        ) {
            Card(
                modifier = Modifier.width(120.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = UiColor.Black.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Elbow Angle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Elbow",
                            style = MaterialTheme.typography.bodySmall,
                            color = UiColor.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "${elbowDeg}°",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (elbowDeg in 85..95) UiColor(0xFF00FF6D) else UiColor(0xFFFFA500)
                        )
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Hip Angle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hip",
                            style = MaterialTheme.typography.bodySmall,
                            color = UiColor.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "${hipDeg}°",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (hipDeg in 170..180) UiColor(0xFF00FF6D) else UiColor(0xFFFFA500)
                        )
                    }
                }
            }
        }
        
        // Center Bottom: Form Feedback Card
        if (formFeedback.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 100.dp)
                    .zIndex(1f)
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            formFeedback.contains("Perfect") -> UiColor(0xFF00FF6D).copy(alpha = 0.9f)
                            formFeedback.contains("Good") -> UiColor(0xFF00E5FF).copy(alpha = 0.9f)
                            else -> UiColor(0xFFFFA500).copy(alpha = 0.9f)
                        }
                    )
                ) {
                    Text(
                        text = formFeedback,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = UiColor.White
                    )
                }
            }
        }
        
        // Finish Workout button (FAB style)
        FloatingActionButton(
            onClick = { showFinishDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(64.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Finish Workout",
                modifier = Modifier.size(32.dp)
            )
        }
        
        // Finish confirmation dialog
        if (showFinishDialog) {
            AlertDialog(
                onDismissRequest = { showFinishDialog = false },
                title = { Text("Complete Workout?") },
                text = {
                    Column {
                        Text("Total Reps: $reps")
                        Text("Good Form: $goodFormReps")
                        Text("Quality: ${if (reps > 0) "${(goodFormReps.toFloat() / reps * 100).toInt()}%" else "0%"}")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showFinishDialog = false
                            // Update ViewModel with current rep counts before stopping
                            viewModel.updateReps(totalReps = reps, goodReps = goodFormReps)
                            viewModel.stopWorkout()
                            navController.navigate(NavRoutes.SessionSummary.route) {
                                popUpTo(NavRoutes.Home.route)
                            }
                        }
                    ) {
                        Text("Finish")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showFinishDialog = false }) {
                        Text("Continue")
                    }
                }
            )
        }
    }
}

private fun processFrame(
    detector: com.google.mlkit.vision.pose.PoseDetector,
    imageProxy: ImageProxy,
    onAngles: (Int, Int) -> Unit,
    onRep: (Int, String?) -> Unit,
    onLandmarks: (Map<Int, Pair<Float, Float>>) -> Unit = {},
    onImageInfo: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onNoPerson: () -> Unit = {}
) {
    val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
    val rotation = imageProxy.imageInfo.rotationDegrees
    val image = InputImage.fromMediaImage(mediaImage, rotation)

    detector.process(image)
        .addOnSuccessListener { pose: Pose ->
            val landmarks = pose.allPoseLandmarks
            if (landmarks.isNullOrEmpty()) {
                // No person detected: reset displayed angles to 0 and clear smoothing
                emaElbow = null
                emaHip = null
                onNoPerson()
                imageProxy.close()
                return@addOnSuccessListener
            }

            val shoulder = getBestSidePoint(landmarks, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
            val elbow = getBestSidePoint(landmarks, PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW)
            val wrist = getBestSidePoint(landmarks, PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)
            val hip = getBestSidePoint(landmarks, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
            val knee = getBestSidePoint(landmarks, PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE)

            if (shoulder != null && elbow != null && wrist != null && hip != null && knee != null) {
                val elbowAngleRaw = calculateAngleDeg(shoulder, elbow, wrist)
                val hipAngleRaw = calculateAngleDeg(shoulder, hip, knee)

                // Simple EMA smoothing for stability
                val smoothed = smoothAngles(elbowAngleRaw, hipAngleRaw)
                onAngles(smoothed.first, smoothed.second)
            }

            // Provide normalized landmarks for overlay drawing
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                var iw = mediaImage.width
                var ih = mediaImage.height
                val rot = imageProxy.imageInfo.rotationDegrees
                if (rot == 90 || rot == 270) {
                    val tmp = iw
                    iw = ih
                    ih = tmp
                }
                onImageInfo(iw, ih, 0)
                val map = landmarks.associate { lm ->
                    lm.landmarkType to Pair(lm.position.x, lm.position.y)
                }
                onLandmarks(map)
            }
        }
        .addOnFailureListener {
            // ignore per-frame failures
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private fun getPoint(all: List<PoseLandmark>, type: Int): P? =
    all.firstOrNull { it.landmarkType == type }?.let { P(it.position.x, it.position.y) }

private fun getBestSidePoint(all: List<PoseLandmark>, left: Int, right: Int): P? {
    val l = all.firstOrNull { it.landmarkType == left }
    val r = all.firstOrNull { it.landmarkType == right }
    val pick = when {
        l == null && r == null -> null
        l != null && r == null -> l
        l == null && r != null -> r
        else -> if ((l!!.inFrameLikelihood) >= (r!!.inFrameLikelihood)) l else r
    }
    return pick?.let { P(it.position.x, it.position.y) }
}

// Exponential moving average smoothing for angles
private var emaElbow: Double? = null
private var emaHip: Double? = null
private const val EMA_ALPHA = 0.2

private fun smoothAngles(elbowRaw: Int, hipRaw: Int): Pair<Int, Int> {
    emaElbow = if (emaElbow == null) elbowRaw.toDouble() else (EMA_ALPHA * elbowRaw + (1 - EMA_ALPHA) * emaElbow!!)
    emaHip = if (emaHip == null) hipRaw.toDouble() else (EMA_ALPHA * hipRaw + (1 - EMA_ALPHA) * emaHip!!)
    return Pair(emaElbow!!.toInt(), emaHip!!.toInt())
}

/**
 * Circular Progress Ring Component
 * Shows progress toward goal reps
 */
@Composable
private fun CircularProgressRing(
    progress: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = 8.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        
        // Background circle
        drawCircle(
            color = UiColor.White.copy(alpha = 0.2f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        // Progress circle
        val sweepAngle = progress * 360f
        val progressColor = when {
            progress >= 1f -> UiColor(0xFF00FF6D) // Green when complete
            progress >= 0.7f -> UiColor(0xFF00E5FF) // Cyan when close
            progress >= 0.4f -> UiColor(0xFFFFA500) // Orange when halfway
            else -> UiColor(0xFFFF6B6B) // Red when starting
        }
        
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2)
        )
    }
}

/**
 * Form Quality Gauge Component
 * Shows form quality as a circular gauge
 */
@Composable
private fun FormQualityGauge(
    percentage: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = 6.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        
        // Background arc (0-100%)
        drawArc(
            color = UiColor.White.copy(alpha = 0.2f),
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2)
        )
        
        // Quality arc
        val sweepAngle = (percentage / 100f) * 180f
        val qualityColor = when {
            percentage >= 80f -> UiColor(0xFF00FF6D) // Green
            percentage >= 60f -> UiColor(0xFF00E5FF) // Cyan
            percentage >= 40f -> UiColor(0xFFFFA500) // Orange
            else -> UiColor(0xFFFF6B6B) // Red
        }
        
        drawArc(
            color = qualityColor,
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2)
        )
    }
}


