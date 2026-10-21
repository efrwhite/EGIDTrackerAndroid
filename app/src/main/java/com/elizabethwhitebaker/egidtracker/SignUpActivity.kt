package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.text.Annotation
import android.text.SpannableString
import android.text.SpannedString
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class SignUpActivity : AppCompatActivity() {

    private lateinit var firstNameEditText: EditText
    private lateinit var lastNameEditText: EditText
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

        firstNameEditText = findViewById(R.id.signUpFirstName)
        lastNameEditText = findViewById(R.id.signUpLastName)
        usernameEditText = findViewById(R.id.signUpUsername)
        phoneEditText = findViewById(R.id.signUpPhone)
        emailEditText = findViewById(R.id.signUpEmail)
        passwordEditText = findViewById(R.id.signUpPassword)
        confirmPasswordEditText = findViewById(R.id.signUpConfirm)
        signUpButton = findViewById(R.id.signUpButton)

        signUpButton.setOnClickListener { signUpUser() }

        styleTermsLinks(findViewById(R.id.termsText))
    }

    /** Colors and underlines the <annotation type="link"> parts of the terms text. */
    private fun styleTermsLinks(textView: TextView) {
        val source = getText(R.string.terms_agreement) as SpannedString
        val styled = SpannableString(source)
        val linkColor = ContextCompat.getColor(this, R.color.lavender)
        source.getSpans(0, source.length, Annotation::class.java)
            .filter { it.key == "type" && it.value == "link" }
            .forEach { link ->
                val start = source.getSpanStart(link)
                val end = source.getSpanEnd(link)
                styled.setSpan(ForegroundColorSpan(linkColor), start, end, 0)
                styled.setSpan(UnderlineSpan(), start, end, 0)
            }
        textView.text = styled
    }

    private fun signUpUser() {
        val firstName = firstNameEditText.text.toString().trim()
        val lastName = lastNameEditText.text.toString().trim()
        val username = usernameEditText.text.toString().trim()
        val phone = phoneEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val confirmPassword = confirmPasswordEditText.text.toString().trim()

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty() ||
            confirmPassword.isEmpty() || username.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isPasswordValid(password)) {
            Toast.makeText(this, R.string.password_requirements_error, Toast.LENGTH_LONG).show()
            return
        }

        // Check uniqueness directly against Users
        checkUsernameUnique(username) { isUnique ->
            if (!isUnique) {
                Toast.makeText(this, "Username is already taken. Please choose another.", Toast.LENGTH_LONG).show()
                return@checkUsernameUnique
            }

            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (!task.isSuccessful) {
                        Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }

                    val userId = firebaseAuth.currentUser?.uid ?: ""
                    val userMap = hashMapOf(
                        "firstName" to firstName,
                        "lastName" to lastName,
                        "email" to email,
                        "username" to username,
                        "phone" to phone
                    )

                    // Save profile in Users/{uid}
                    Firebase.firestore.collection("Users").document(userId)
                        .set(userMap)
                        .addOnSuccessListener {
                            saveUsernameToSharedPreferences(username)

                            // Move forward (no more Usernames write)
                            val intent = Intent(this, AddCaregiverActivity::class.java).apply {
                                putExtra("username", username)
                                putExtra("isFirstTimeUser", true)
                            }
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to create user profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
        }
    }

    private fun saveUsernameToSharedPreferences(username: String) {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        sharedPreferences.edit().putString("CurrentUsername", username).apply()
    }

    private fun isPasswordValid(password: String): Boolean {
        val passwordPattern = "^(?=.*[A-Z])(?=.*[!@#\\$])[A-Za-z\\d!@#\\$]{7,}$"
        return Regex(passwordPattern).matches(password)
    }

    private fun checkUsernameUnique(username: String, onComplete: (Boolean) -> Unit) {
        Firebase.firestore.collection("Users")
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                onComplete(snapshot.isEmpty)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to check username uniqueness: ${e.message}", Toast.LENGTH_SHORT).show()
                onComplete(false)
            }
    }
}
