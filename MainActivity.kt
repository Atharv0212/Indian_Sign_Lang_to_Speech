package com.example.speechtotextapp

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var txtResult: TextView
    private lateinit var imgSign: ImageView
    private lateinit var txtCurrentChar: TextView
    private lateinit var auth: FirebaseAuth
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val btnSpeak = findViewById<Button>(R.id.btnSpeak)
        val btnCameraMode = findViewById<Button>(R.id.btnCameraMode)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        txtResult = findViewById(R.id.txtResult)
        imgSign = findViewById(R.id.imgSign)
        txtCurrentChar = findViewById(R.id.txtCurrentChar)

        // Show hello GIF by default
        val helloResId = resources.getIdentifier("hello", "drawable", packageName)
        if (helloResId != 0) {
            Glide.with(this).asGif().load(helloResId).into(imgSign)
            txtCurrentChar.text = "Greeting: Hello"
        } else {
            txtCurrentChar.text = "Welcome!"
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        btnSpeak.setOnClickListener {
            startListening()
        }

        btnCameraMode.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(this, CameraActivity::class.java))
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 2)
            }
        }

        // LOGOUT BUTTON
        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->

                    // Sign out from Firebase
                    auth.signOut()

                    // FIX: Also sign out from Google to clear the cached account,
                    // otherwise tapping "Login with Google" skips the picker entirely
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken("1009932984845-msj84ahipia0k4k6lsmnl9ka1fe5j8pj.apps.googleusercontent.com")
                        .requestEmail()
                        .build()
                    val googleSignInClient = GoogleSignIn.getClient(this, gso)
                    googleSignInClient.signOut().addOnCompleteListener {
                        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, CameraActivity::class.java))
        }
    }

    private fun startListening() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                txtResult.text = "Listening..."
                Glide.with(this@MainActivity).clear(imgSign)
                imgSign.setImageResource(0)
                txtCurrentChar.text = "-"
            }

            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.get(0) ?: "No speech detected"
                txtResult.text = "Spoken: $spokenText"

                val islText = reorderToISL(spokenText)
                val sequence = getSignResources(islText)

                if (sequence.isNotEmpty()) {
                    playSignSequence(sequence)
                } else {
                    txtResult.append("\n(No signs found)")
                }
            }

            override fun onError(error: Int) {
                txtResult.text = "Error code: $error. Tap Speak again."
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
    }

    private fun reorderToISL(spokenText: String): String {
        val words = spokenText.lowercase().split(" ").toMutableList()
        val stopWords = setOf("is", "am", "are", "the", "a", "an", "to", "of", "will")
        words.removeAll { stopWords.contains(it) }

        if (words.size == 3) {
            val subject = words[0]
            val verb = words[1]
            val _object = words[2]
            return "$subject $_object $verb"
        }
        return words.joinToString(" ")
    }

    private fun getSignResources(islText: String): List<Pair<String, Int>> {
        val sequence = mutableListOf<Pair<String, Int>>()
        val cleanText = islText.replace(Regex("[^a-z0-9 ]"), "")

        for (char in cleanText) {
            var resourceName = ""

            if (char.isLetter()) {
                resourceName = char.toString()
            } else if (char.isDigit()) {
                resourceName = "n$char"
            }

            if (resourceName.isNotEmpty()) {
                val resId = resources.getIdentifier(resourceName, "drawable", packageName)
                if (resId != 0) {
                    sequence.add(Pair(char.toString().uppercase(), resId))
                }
            }
        }
        return sequence
    }

    private fun playSignSequence(sequence: List<Pair<String, Int>>) {
        var index = 0

        val runnable = object : Runnable {
            override fun run() {
                if (index < sequence.size) {
                    val item = sequence[index]

                    txtCurrentChar.text = item.first
                    Glide.with(this@MainActivity).clear(imgSign)
                    imgSign.setImageResource(item.second)

                    index++
                    handler.postDelayed(this, 800)
                }
            }
        }
        handler.post(runnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        handler.removeCallbacksAndMessages(null)
    }
}
