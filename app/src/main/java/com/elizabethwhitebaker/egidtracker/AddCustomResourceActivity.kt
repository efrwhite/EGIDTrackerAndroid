package com.elizabethwhitebaker.egidtracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AddCustomResourceActivity : AppCompatActivity() {

    private lateinit var titleEditText: EditText
    private lateinit var urlEditText: EditText
    private lateinit var notesEditText: EditText
    private lateinit var saveButton: Button

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private var resourceId: String? = null
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_custom_resource)

        titleEditText = findViewById(R.id.nameField)
        urlEditText = findViewById(R.id.urlField)
        notesEditText = findViewById(R.id.notesField)
        saveButton = findViewById(R.id.saveButton)

        isEditMode = intent.getBooleanExtra("isEditMode", false)
        resourceId = intent.getStringExtra("resourceId")

        if (isEditMode && resourceId != null) {
            fetchAndPopulateResourceData(resourceId!!)
        }

        saveButton.setOnClickListener {
            if (isEditMode && resourceId != null) updateResource(resourceId!!) else saveNewResource()
        }
    }

    private fun saveNewResource() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Please sign in.", Toast.LENGTH_SHORT).show()
            return
        }

        val resourceMap = hashMapOf(
            "title" to titleEditText.text.toString().trim(),
            "url" to urlEditText.text.toString().trim(),
            "notes" to notesEditText.text.toString().trim(),
            "userId" to uid
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
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Please sign in.", Toast.LENGTH_SHORT).show()
            return
        }

        val resourceMap = hashMapOf(
            "title" to titleEditText.text.toString().trim(),
            "url" to urlEditText.text.toString().trim(),
            "notes" to notesEditText.text.toString().trim(),
            "userId" to uid
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
        finish()
    }
}
