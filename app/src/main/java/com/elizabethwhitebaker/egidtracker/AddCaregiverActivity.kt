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

class AddCaregiverActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var firstNameEditText: EditText
    private lateinit var lastNameEditText: EditText
    private lateinit var saveButton: Button
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var mode: String = "add"
    private var caregiverId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caregiver_profile)

        usernameEditText = findViewById(R.id.editText2)
        firstNameEditText = findViewById(R.id.editText)
        lastNameEditText = findViewById(R.id.editText3)
        saveButton = findViewById(R.id.saveButton)

        // Retrieve username from intent
        val username = intent.getStringExtra("username")
        if (username != null) {
            usernameEditText.setText(username)
            usernameEditText.isEnabled = false  // Disable editing if username is preset
        }

        // Check if we are in "edit" mode
        caregiverId = intent.getStringExtra("caregiverId")
        if (caregiverId != null) {
            mode = "edit"
            populateCaregiverData(caregiverId!!)
        }

        saveButton.setOnClickListener {
            if (mode == "add") {
                saveCaregiverInfo()
            } else {
                updateCaregiverInfo()
            }
        }
    }


    private fun populateCaregiverData(caregiverId: String) {
        // Fetch the caregiver document to get the parentUserId
        Firebase.firestore.collection("Caregivers").document(caregiverId).get()
            .addOnSuccessListener { caregiverSnapshot ->
                if (caregiverSnapshot.exists()) {
                    // Extract the parentUserId which links to the user document
                    val parentUserId = caregiverSnapshot.getString("parentUserId") ?: ""

                    // Use the parentUserId to fetch the username from the Usernames collection
                    fetchUsername(parentUserId)

                    // Set other caregiver details
                    firstNameEditText.setText(caregiverSnapshot.getString("firstName") ?: "")
                    lastNameEditText.setText(caregiverSnapshot.getString("lastName") ?: "")

                } else {
                    Toast.makeText(this, "Caregiver not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching caregiver details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchUsername(parentUserId: String) {
        // Since document IDs are usernames and contain a 'userId' field for matching,
        // the query should fetch based on 'userId' being equal to parentUserId.
        Firebase.firestore.collection("Usernames")
            .whereEqualTo("userId", parentUserId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.documents.isNotEmpty()) {
                    // Assuming the document ID (username) is what we need to display
                    val username = documents.documents.first().id
                    usernameEditText.setText(username)
                    usernameEditText.isEnabled = false // Make the EditText uneditable
                } else {
                    Toast.makeText(this, "Username not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching username: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveCaregiverInfo() {
        val firstName = firstNameEditText.text.toString().trim()
        val lastName = lastNameEditText.text.toString().trim()

        if (firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
            return
        }

        val userMap = hashMapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "parentUserId" to firebaseAuth.currentUser?.uid
        )

        Firebase.firestore.collection("Caregivers").add(userMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Caregiver added successfully", Toast.LENGTH_SHORT).show()
                goToNextActivity()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to add caregiver: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateCaregiverInfo() {
        val firstName = firstNameEditText.text.toString().trim()
        val lastName = lastNameEditText.text.toString().trim()

        if (firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
            return
        }

        caregiverId?.let { id ->
            val userMap = hashMapOf(
                "firstName" to firstName,
                "lastName" to lastName,
                "parentUserId" to firebaseAuth.currentUser?.uid
            )

            Firebase.firestore.collection("Caregivers").document(id).set(userMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Caregiver updated successfully", Toast.LENGTH_SHORT).show()
                    goToNextActivity()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update caregiver: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: Toast.makeText(this, "Error: Caregiver ID is missing.", Toast.LENGTH_SHORT).show()
    }

    private fun goToNextActivity() {
        val nextActivityIntent = when {
            intent.getBooleanExtra("isFirstTimeUser", false) && mode == "add" -> {
                Intent(this, AddChildActivity::class.java).apply {
                    putExtra("isFirstTimeUser", true)
                }
            }
            else -> {
                Intent(this, ProfilesActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        }
        startActivity(nextActivityIntent)
        finish()
    }
}
