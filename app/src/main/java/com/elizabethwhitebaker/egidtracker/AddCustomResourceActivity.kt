package com.elizabethwhitebaker.egidtracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class AddCustomResourceActivity : AppCompatActivity() {

    private lateinit var titleEditText: EditText
    private lateinit var urlEditText: EditText
    private lateinit var notesEditText: EditText
    private lateinit var saveButton: Button

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private var resourceId: String? = null
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_custom_resource)

        // Initialize UI elements
        titleEditText = findViewById(R.id.nameField)
        urlEditText = findViewById(R.id.urlField)
        notesEditText = findViewById(R.id.notesField)
        saveButton = findViewById(R.id.saveButton)

        isEditMode = intent.getBooleanExtra("isEditMode", false)
        resourceId = intent.getStringExtra("resourceId")

        if (isEditMode && resourceId != null) {
            // Fetch resource data if in edit mode
            fetchAndPopulateResourceData(resourceId!!)
        }

        saveButton.setOnClickListener {
            if (isEditMode && resourceId != null) {
                updateResource(resourceId!!)
            } else {
                saveNewResource()
            }
        }
    }

    private fun saveNewResource() {
        val username = getCurrentUsername()
        if (username == null) {
            Toast.makeText(this, "Username not found. Unable to save resource.", Toast.LENGTH_SHORT).show()
            return
        }

        val resourceMap = hashMapOf(
            "title" to titleEditText.text.toString().trim(),
            "url" to urlEditText.text.toString().trim(),
            "notes" to notesEditText.text.toString().trim(),
            "username" to username
        )

        firestore.collection("CustomResources").add(resourceMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Resource added successfully", Toast.LENGTH_SHORT).show()
                navigateBackToResources()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to add resource: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateResource(resourceId: String) {
        val username = getCurrentUsername()
        if (username == null) {
            Toast.makeText(this, "Username not found. Unable to update resource.", Toast.LENGTH_SHORT).show()
            return
        }

        val resourceMap = hashMapOf(
            "title" to titleEditText.text.toString().trim(),
            "url" to urlEditText.text.toString().trim(),
            "notes" to notesEditText.text.toString().trim(),
            "username" to username
        )

        firestore.collection("CustomResources").document(resourceId).set(resourceMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Resource updated successfully", Toast.LENGTH_SHORT).show()
                navigateBackToResources()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update resource: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchAndPopulateResourceData(resourceId: String) {
        firestore.collection("CustomResources").document(resourceId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    titleEditText.setText(document.getString("title"))
                    urlEditText.setText(document.getString("url"))
                    notesEditText.setText(document.getString("notes"))
                } else {
                    Toast.makeText(this, "Resource not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load resource data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateBackToResources() {
        val intent = Intent(this, CustomResourcesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    // Retrieve the current username from SharedPreferences
    private fun getCurrentUsername(): String? {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("CurrentUsername", null)
    }
}
