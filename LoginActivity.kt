package com.example.speechtotextapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val RC_SIGN_IN = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val emailEdit = findViewById<EditText>(R.id.emailEdit)
        val passwordEdit = findViewById<EditText>(R.id.passwordEdit)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val signupButton = findViewById<Button>(R.id.signupButton)
        val googleButton = findViewById<Button>(R.id.googleLoginButton)

        // GOOGLE SIGN IN CONFIG
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("1009932984845-msj84ahipia0k4k6lsmnl9ka1fe5j8pj.apps.googleusercontent.com")
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // EMAIL LOGIN
        loginButton.setOnClickListener {

            val email = emailEdit.text.toString().trim()
            val password = passwordEdit.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->

                    if (task.isSuccessful) {

                        val user = auth.currentUser

                        // CHECK EMAIL VERIFICATION
                        if (user != null && !user.isEmailVerified) {
                            // Sign out immediately — unverified users can't access the app
                            auth.signOut()
                            Toast.makeText(
                                this,
                                "Please verify your email before logging in. Check your inbox.",
                                Toast.LENGTH_LONG
                            ).show()

                            // Auto-resend verification email on failed login attempt
                            user.sendEmailVerification()
                                .addOnCompleteListener { resendTask ->
                                    if (resendTask.isSuccessful) {
                                        Toast.makeText(
                                            this,
                                            "Verification email resent to $email",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                        } else {
                            // Email is verified — proceed to app
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }

                    } else {
                        Toast.makeText(this, task.exception?.message, Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // GOOGLE LOGIN BUTTON
        // Google accounts are inherently verified — no extra email check needed
        googleButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        signupButton.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {

                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)

                auth.signInWithCredential(credential)
                    .addOnCompleteListener { task ->

                        if (task.isSuccessful) {

                            startActivity(Intent(this, MainActivity::class.java))
                            finish()

                        } else {

                            Toast.makeText(this, "Google login failed", Toast.LENGTH_SHORT).show()

                        }
                    }

            } catch (e: ApiException) {

                Toast.makeText(this, "Google sign in failed", Toast.LENGTH_SHORT).show()

            }
        }
    }
}
