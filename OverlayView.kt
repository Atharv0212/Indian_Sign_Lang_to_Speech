package com.example.speechtotextapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class OverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var landmarks: List<NormalizedLandmark> = emptyList()

    // Scale factors — updated each frame to match the preview dimensions
    private var scaleX = 1f
    private var scaleY = 1f

    // --- Paints ---
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val fingerTipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }

    private val wristPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    // MediaPipe hand landmark connections
    // Each pair is (start landmark index, end landmark index)
    private val connections = listOf(
        // Palm
        0 to 1, 1 to 2, 2 to 3, 3 to 4,
        // Thumb
        0 to 5, 5 to 6, 6 to 7, 7 to 8,
        // Index finger
        0 to 9, 9 to 10, 10 to 11, 11 to 12,
        // Middle finger
        9 to 13, 13 to 14, 14 to 15, 15 to 16,
        // Ring finger
        13 to 17, 17 to 18, 18 to 19, 19 to 20,
        // Pinky
        0 to 17, 5 to 9
    )

    // Fingertip landmark indices
    private val fingerTips = setOf(4, 8, 12, 16, 20)

    fun updateLandmarks(
        newLandmarks: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int
    ) {
        landmarks = newLandmarks
        // Compute how the image maps onto this view, accounting for aspect ratio
        scaleX = width.toFloat() / imageWidth
        scaleY = height.toFloat() / imageHeight
        invalidate() // triggers onDraw
    }

    fun clear() {
        landmarks = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (landmarks.isEmpty()) return

        // Draw connections (skeleton lines)
        for ((start, end) in connections) {
            if (start < landmarks.size && end < landmarks.size) {
                val s = landmarks[start]
                val e = landmarks[end]
                canvas.drawLine(
                    s.x() * width,
                    s.y() * height,
                    e.x() * width,
                    e.y() * height,
                    linePaint
                )
            }
        }

        // Draw each landmark dot
        for ((index, lm) in landmarks.withIndex()) {
            val cx = lm.x() * width
            val cy = lm.y() * height

            val paint = when (index) {
                0 -> wristPaint          // Wrist = red
                in fingerTips -> fingerTipPaint  // Fingertips = yellow
                else -> dotPaint         // Everything else = cyan
            }
            val radius = if (index in fingerTips || index == 0) 16f else 10f
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }
}
