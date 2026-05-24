package com.example.speechtotextapp

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: OverlayView   // NEW
    private lateinit var tvCurrentChar: TextView
    private lateinit var tvSentence: TextView
    private lateinit var btnSpace: Button
    private lateinit var btnDelete: Button

    private lateinit var cameraExecutor: ExecutorService

    // Debouncing logic
    private var lastDetectedChar = ""
    private var consecutiveCount = 0
    private val DEBOUNCE_THRESHOLD = 20
    private var sentence = StringBuilder()
    private var isLocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewFinder   = findViewById(R.id.viewFinder)
        overlayView  = findViewById(R.id.overlayView)   // NEW
        tvCurrentChar = findViewById(R.id.tvCurrentChar)
        tvSentence   = findViewById(R.id.tvSentence)
        btnSpace     = findViewById(R.id.btnSpace)
        btnDelete    = findViewById(R.id.btnDelete)

        cameraExecutor = Executors.newSingleThreadExecutor()

        btnSpace.setOnClickListener {
            sentence.append(" ")
            updateSentenceUI()
        }

        btnDelete.setOnClickListener {
            if (sentence.isNotEmpty()) {
                sentence.deleteCharAt(sentence.length - 1)
                updateSentenceUI()
            }
        }

        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(viewFinder.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val analyzer = HandSignAnalyzer(
                context = this,
                onSignDetected = { detectedChar ->
                    runOnUiThread { handleDetection(detectedChar) }
                },
                // NEW: receive landmarks and forward to OverlayView
                onLandmarksDetected = { landmarks, imageWidth, imageHeight ->
                    runOnUiThread {
                        overlayView.updateLandmarks(landmarks, imageWidth, imageHeight)
                    }
                },
                // NEW: clear overlay when no hand is visible
                onNoHandDetected = {
                    runOnUiThread { overlayView.clear() }
                }
            )

            imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                Toast.makeText(this, "Failed to start camera.", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleDetection(detectedChar: String) {
        tvCurrentChar.text = detectedChar

        if (detectedChar == lastDetectedChar) {
            consecutiveCount++
        } else {
            consecutiveCount = 0
            lastDetectedChar = detectedChar
            isLocked = false
        }

        if (consecutiveCount >= DEBOUNCE_THRESHOLD && !isLocked) {
            sentence.append(detectedChar)
            updateSentenceUI()
            isLocked = true

            tvCurrentChar.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
            tvCurrentChar.postDelayed({
                tvCurrentChar.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            }, 500)
        }
    }

    private fun updateSentenceUI() {
        tvSentence.text = sentence.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraActivity"
    }
}
