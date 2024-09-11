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

class SignUpActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var signUpButton: Button
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        firebaseAuth = FirebaseAuth.getInstance()

        // Initialization
        usernameEditText = findViewById(R.id.signUpUsername)
        phoneEditText = findViewById(R.id.signUpPhone)
        emailEditText = findViewById(R.id.signUpEmail)
        passwordEditText = findViewById(R.id.signUpPassword)
        confirmPasswordEditText = findViewById(R.id.signUpConfirm)
        signUpButton = findViewById(R.id.signUpButton)

        signUpButton.setOnClickListener {
            signUpUser()
        }
    }

    private fun signUpUser() {
        val username = usernameEditText.text.toString().trim()
        val phone = phoneEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val confirmPassword = confirmPasswordEditText.text.toString().trim()

        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || username.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isPasswordValid(password)) {
            Toast.makeText(this, "Password does not meet the requirements.", Toast.LENGTH_SHORT).show()
            return
        }

        checkUsernameUnique(username) { isUnique ->
            if (isUnique) {
                firebaseAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Create and save user profile
                        val userId = firebaseAuth.currentUser?.uid ?: ""
                        val userMap = hashMapOf("email" to email, "username" to username, "phone" to phone)
                        Firebase.firestore.collection("Users").document(userId).set(userMap).addOnSuccessListener {
                            // Save the username mapping
                            Firebase.firestore.collection("Usernames").document(username).set(mapOf("userId" to userId)).addOnSuccessListener {
                                // Transition to AddCaregiverActivity with the username passed as an extra
                                val intent = Intent(this, AddCaregiverActivity::class.java).apply {
                                    putExtra("username", username) // Pass the username for use in the next activity
                                    putExtra("isFirstTimeUser", true)
                                }
                                startActivity(intent)
                                finish() // Finish to prevent returning to signup screen
                            }
                        }.addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to create user profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Username is already taken. Please choose another.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isPasswordValid(password: String): Boolean {
        val passwordPattern = "^(?=.*[A-Z])(?=.*[!@#\$])[A-Za-z\\d!@#\$]{7,}$"
        return Regex(passwordPattern).matches(password)
    }

    private fun checkUsernameUnique(username: String, onComplete: (Boolean) -> Unit) {
        Firebase.firestore.collection("Usernames").document(username).get().addOnSuccessListener { document ->
            onComplete(!document.exists())
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to check username uniqueness: ${e.message}", Toast.LENGTH_SHORT).show()
            onComplete(false)
        }
    }
}
