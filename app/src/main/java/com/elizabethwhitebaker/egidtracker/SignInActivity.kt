package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class SignInActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var signInButton: Button
    private lateinit var signUpLink: Button
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signin)

        firebaseAuth = FirebaseAuth.getInstance()

        usernameEditText = findViewById(R.id.signInUsername)
        passwordEditText = findViewById(R.id.signInPassword)
        signInButton = findViewById(R.id.signInButton)
        signUpLink = findViewById(R.id.signUpLink)

        signInButton.setOnClickListener {
            signInUser()
        }

        signUpLink.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
            finish()
        }
    }

    private fun signInUser() {
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (username.isNotEmpty() && password.isNotEmpty()) {
            // Look up the email associated with the username
            Firebase.firestore.collection("Users").whereEqualTo("username", username).get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        Toast.makeText(this, "Invalid username or password.", Toast.LENGTH_SHORT).show()
                    } else {
                        val email = documents.documents[0].getString("email")
                        if (email != null) {
                            // Use the email to sign in with Firebase Auth
                            firebaseAuth.signInWithEmailAndPassword(email, password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        // Navigate to HomeActivity or the next appropriate activity
                                        startActivity(Intent(this, HomeActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        })
                                        finish()
                                    } else {
                                        Toast.makeText(this, "Sign-in failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        } else {
                            Toast.makeText(this, "Failed to retrieve email for username.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Failed to retrieve user data: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "Username and password are required.", Toast.LENGTH_SHORT).show()
        }
    }
}

