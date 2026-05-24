package com.example.speechtotextapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.pow
import kotlin.math.sqrt

class HandSignAnalyzer(
    context: Context,
    private val onSignDetected: (String) -> Unit,
    // NEW: callback to pass landmarks + image size for overlay drawing
    private val onLandmarksDetected: (List<NormalizedLandmark>, Int, Int) -> Unit = { _, _, _ -> },
    private val onNoHandDetected: () -> Unit = {}
) : ImageAnalysis.Analyzer {

    private var handLandmarker: HandLandmarker? = null

    init {
        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath("hand_landmarker.task")
        val baseOptions = baseOptionsBuilder.build()

        val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setNumHands(2)
            .setRunningMode(RunningMode.IMAGE)

        try {
            handLandmarker = HandLandmarker.createFromOptions(context, optionsBuilder.build())
        } catch (e: Exception) {
            Log.e("HandSignAnalyzer", "MediaPipe failed to load model", e)
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        val landmarker = handLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        val bitmap = imageProxy.toBitmap()
        val matrix = Matrix()
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // Image dimensions after rotation (width/height may swap on 90/270 degree rotation)
        val imageWidth = rotatedBitmap.width
        val imageHeight = rotatedBitmap.height

        val mpImage: MPImage = BitmapImageBuilder(rotatedBitmap).build()
        val result = landmarker.detect(mpImage)

        if (result != null && result.landmarks().isNotEmpty()) {
            // Pass the first detected hand's landmarks back to CameraActivity for overlay drawing
            onLandmarksDetected(result.landmarks()[0], imageWidth, imageHeight)

            // --- Sign recognition logic (supports up to 2 hands) ---
            val normalizedCombined = FloatArray(126) { 0f }

            for (i in 0 until result.landmarks().size) {
                val landmarks = result.landmarks()[i]
                
                // Determine Handedness. Defaulting to Right if missing.
                var handednessCategory = "Right"
                val handednessesOpt = result.handednesses()
                if (handednessesOpt.isPresent && i < handednessesOpt.get().size) {
                    handednessCategory = handednessesOpt.get()[i][0].categoryName()
                }

                val wrist = landmarks[0]
                val shifted = landmarks.map { point: NormalizedLandmark ->
                    FloatArray(3) { j ->
                        when (j) {
                            0 -> point.x() - wrist.x()
                            1 -> point.y() - wrist.y()
                            2 -> point.z() - wrist.z()
                            else -> 0f
                        }
                    }
                }

                val mcp = shifted[9]
                val scale = sqrt(mcp[0].pow(2) + mcp[1].pow(2) + mcp[2].pow(2))
                val safeScale = if (scale == 0f) 1f else scale

                // Offset left hand to 0, right hand to 63
                val startIndex = if (handednessCategory == "Left") 0 else 63
                var index = startIndex
                for (point in shifted) {
                    normalizedCombined[index++] = point[0] / safeScale
                    normalizedCombined[index++] = point[1] / safeScale
                    normalizedCombined[index++] = point[2] / safeScale
                }
            }

            var minDistance = Float.MAX_VALUE
            var bestMatch = ""

            try {
                for ((label, template) in SignTemplates.templates) {
                    var distance = 0f
                    // Distance checked across 126 elements (42 coordinates)
                    for (i in 0 until 126) {
                        distance += (normalizedCombined[i] - template[i]).pow(2)
                    }
                    distance = sqrt(distance)
                    if (distance < minDistance) {
                        minDistance = distance
                        bestMatch = label
                    }
                }
                if (minDistance < 2.0f && bestMatch.isNotEmpty()) {
                    onSignDetected(bestMatch)
                }
            } catch (e: NoClassDefFoundError) {
                // SignTemplates not yet generated
            }
        } else {
            // No hand in frame — tell the overlay to clear itself
            onNoHandDetected()
        }

        imageProxy.close()
    }
}
