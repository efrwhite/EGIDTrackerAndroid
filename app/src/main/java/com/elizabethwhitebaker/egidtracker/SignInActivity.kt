package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SignInActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var signInButton: Button
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signin)

        firebaseAuth = FirebaseAuth.getInstance()

        usernameEditText = findViewById(R.id.signInUsername)
        passwordEditText = findViewById(R.id.signInPassword)
        signInButton = findViewById(R.id.signInButton)

        signInButton.setOnClickListener {
            signInUser()
        }

        findViewById<TextView>(R.id.forgotPassword).setOnClickListener { sendPasswordReset() }

        // Social sign-in isn't set up in Firebase yet; the buttons are in place for the design
        mapOf(
            R.id.appleButton to "Apple",
            R.id.googleButton to "Google",
            R.id.facebookButton to "Facebook"
        ).forEach { (id, provider) ->
            findViewById<View>(id).setOnClickListener {
                Toast.makeText(this, getString(R.string.social_login_unavailable, provider), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Users sign in with a username, so look up its email before sending the reset link. */
    private fun sendPasswordReset() {
        val username = usernameEditText.text.toString().trim()
        if (username.isEmpty()) {
            Toast.makeText(this, R.string.forgot_password_need_username, Toast.LENGTH_SHORT).show()
            usernameEditText.requestFocus()
            return
        }

        Usernames.lookupEmail(username)
            .addOnSuccessListener { email ->
                if (email.isNullOrBlank()) {
                    Toast.makeText(this, "No account found for that username.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                firebaseAuth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        Toast.makeText(this, R.string.forgot_password_sent, Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to send reset email: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to retrieve user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun signInUser() {
        val username = usernameEditText.text.toString().trim()
        // Not trimmed: spaces are part of the password
        val password = passwordEditText.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Username and password are required.", Toast.LENGTH_SHORT).show()
            return
        }

        Usernames.lookupEmail(username)
            .addOnSuccessListener { email ->
                if (email.isNullOrBlank()) {
                    Toast.makeText(this, "Invalid username or password.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                firebaseAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            startActivity(Intent(this, HomeActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        } else {
                            Toast.makeText(this, "Sign-in failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to retrieve user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
