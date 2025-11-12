package com.example.fitnesstracker.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.PI

enum class RepQuality {
    EXCELLENT,   // ≤90° elbow + ±15° hip
    GOOD,        // ≤100° elbow + ±30° hip
    ACCEPTABLE   // Valid but needs work
}

data class RepState(
    val reps: Int,
    val goodFormReps: Int,
    val stage: String?,
    val elbowDeg: Int,
    val hipDeg: Int,
    val formFeedback: String,
    val repQuality: RepQuality
)

class PoseRepCounter(
    private val elbowDownMax: Int = 110,        // More realistic than 90°
    private val elbowPerfect: Int = 90,         // For EXCELLENT quality
    private val elbowUpMin: Int = 125,          // >= 125° counts as top (more forgiving, doesn't require fully straight)
    private val enforceHipForm: Boolean = false, // Hip warns but doesn't block by default
    private val hipWarnThreshold: Int = 30,     // More forgiving
    private val hipPerfectThreshold: Int = 15,  // For EXCELLENT quality
    private val minRepTimeMs: Long = 500        // Prevents double-counting
) {
    private var stage: String? = null
    private var reps: Int = 0
    private var goodReps: Int = 0
    private var lastRepTime: Long = 0
    
    // For robust angle smoothing
    private val angleHistory = ArrayDeque<Int>(5)

    fun reset() {
        stage = null
        reps = 0
        goodReps = 0
        lastRepTime = 0
        angleHistory.clear()
    }

    fun update(elbowDeg: Int, hipDeg: Int): RepState {
        // Apply robust smoothing to elbow angle
        val smoothedElbow = smoothAngleRobust(elbowDeg)
        
        // Check hip form
        val hipDiff = abs(hipDeg - 180)
        val hipOk = hipDiff < hipWarnThreshold
        val hipPerfect = hipDiff < hipPerfectThreshold
        
        // Assess rep quality
        val repQuality = when {
            smoothedElbow <= elbowPerfect && hipPerfect -> RepQuality.EXCELLENT
            smoothedElbow <= 100 && hipOk -> RepQuality.GOOD
            else -> RepQuality.ACCEPTABLE
        }
        
        // Generate dynamic feedback
        val formFeedback = when {
            hipDiff > 40 && hipDeg < 180 -> "Engage core - hips sagging"
            hipDiff > 40 && hipDeg > 180 -> "Lower your hips"
            smoothedElbow > 105 -> "Try going deeper"
            repQuality == RepQuality.EXCELLENT -> "Perfect form! 🔥"
            repQuality == RepQuality.GOOD -> "Good form!"
            else -> "Keep it up!"
        }

        // Check if we're at the top position
        if (smoothedElbow > elbowUpMin) {
            stage = "up"
        }
        
        // Check for rep completion
        val currentTime = System.currentTimeMillis()
        val timeSinceLastRep = currentTime - lastRepTime
        
        if (smoothedElbow < elbowDownMax && 
            stage == "up" && 
            timeSinceLastRep > minRepTimeMs) {
            
            // Hip doesn't block by default (unless enforceHipForm = true)
            val shouldCount = !enforceHipForm || hipOk
            
            if (shouldCount) {
                stage = "down"
                reps += 1
                lastRepTime = currentTime
                
                // Track good form reps separately
                if (repQuality != RepQuality.ACCEPTABLE) {
                    goodReps += 1
                }
            }
        }

        return RepState(
            reps = reps,
            goodFormReps = goodReps,
            stage = stage,
            elbowDeg = smoothedElbow,
            hipDeg = hipDeg,
            formFeedback = formFeedback,
            repQuality = repQuality
        )
    }
    
    /**
     * Robust angle smoothing using median filter + outlier removal
     * More resistant to ML Kit noise than simple EMA
     */
    private fun smoothAngleRobust(angle: Int): Int {
        angleHistory.addLast(angle)
        if (angleHistory.size > 5) {
            angleHistory.removeFirst()
        }
        
        if (angleHistory.size < 3) {
            return angle
        }
        
        // Get median
        val sorted = angleHistory.sorted()
        val median = sorted[sorted.size / 2]
        
        // Filter outliers (keep values within 20° of median)
        val filtered = angleHistory.filter { abs(it - median) < 20 }
        
        return if (filtered.isNotEmpty()) {
            filtered.average().toInt()
        } else {
            median
        }
    }

    companion object {
        data class P(val x: Float, val y: Float)

        fun calculateAngleDeg(a: P, b: P, c: P): Int {
            val rad = atan2((c.y - b.y), (c.x - b.x)) - atan2((a.y - b.y), (a.x - b.x))
            var ang = kotlin.math.abs(rad * 180.0 / PI)
            if (ang > 180.0) ang = 360.0 - ang
            return ang.toInt()
        }
    }
}






