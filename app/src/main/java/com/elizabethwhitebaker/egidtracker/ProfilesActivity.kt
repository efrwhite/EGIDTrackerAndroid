package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ProfilesActivity : AppCompatActivity() {

    private lateinit var activePatientBadge: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profiles)

        val addChildLink: Button = findViewById(R.id.addChildlink)
        val addCaregiverLink: Button = findViewById(R.id.addCaregiverlink)
        val childrenTableLayout: TableLayout = findViewById(R.id.childTableLayout)
        val caregiversTableLayout: TableLayout = findViewById(R.id.caregiverTableLayout)

        activePatientBadge = findViewById(R.id.activePatientBadge)

        updateActivePatientBadge(getCurrentChildName())

        addChildLink.setOnClickListener {
            startActivity(Intent(this, AddChildActivity::class.java))
        }

        addCaregiverLink.setOnClickListener {
            startActivity(Intent(this, AddCaregiverActivity::class.java))
        }

        fetchAndDisplayChildren(childrenTableLayout)
        fetchAndDisplayCaregivers(caregiversTableLayout)
    }

    private fun fetchAndDisplayChildren(table: TableLayout) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val currentChildId = getCurrentChildId()

        Firebase.firestore.collection("Children")
            .whereEqualTo("parentUserId", userId)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val childName = document.getString("firstName") ?: "No Name"
                    val childId = document.id

                    // If this document is the active child, sync badge text (and saved name)
                    if (currentChildId != null && childId == currentChildId) {
                        saveCurrentChildName(childName)
                        updateActivePatientBadge(childName)
                    }

                    addRowToTable(table, childId, childName, true)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load children profiles: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchAndDisplayCaregivers(table: TableLayout) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Firebase.firestore.collection("Caregivers")
            .whereEqualTo("parentUserId", userId)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val caregiverName = document.getString("firstName") ?: "No Name"
                    val caregiverId = document.id
                    addRowToTable(table, caregiverId, caregiverName, false)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load caregiver profiles: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addRowToTable(table: TableLayout, id: String, name: String, isChild: Boolean) {
        val row = layoutInflater.inflate(R.layout.table_row_item, table, false)
        val nameTextView = row.findViewById<TextView>(R.id.nameTextView)
        val editButton = row.findViewById<Button>(R.id.editButton)

        nameTextView.text = name
        nameTextView.setOnClickListener {
            handleChildNameClick(id, name, isChild)
        }

        editButton.setOnClickListener {
            navigateToEditActivity(id, isChild)
        }

        table.addView(row)
    }

    private fun handleChildNameClick(childId: String, childName: String, isChild: Boolean) {
        if (isChild) {
            val currentChildId = getCurrentChildId()
            if (childId != currentChildId) {
                saveCurrentChildId(childId)
                saveCurrentChildName(childName)
                updateActivePatientBadge(childName)
                navigateToHome()
            } else {
                Toast.makeText(this, "Currently selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToEditActivity(id: String, isChild: Boolean) {
        val intent = if (isChild) {
            Intent(this, AddChildActivity::class.java).apply {
                putExtra("childId", id)
                putExtra("editMode", true)
            }
        } else {
            Intent(this, AddCaregiverActivity::class.java).apply {
                putExtra("caregiverId", id)
                putExtra("editMode", true)
            }
        }
        startActivity(intent)
    }

    private fun getCurrentChildId(): String? {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return sharedPreferences.getString("CurrentChildId", null)
    }

    private fun saveCurrentChildId(childId: String) {
        getSharedPreferences("AppPreferences", MODE_PRIVATE)
            .edit()
            .putString("CurrentChildId", childId)
            .apply()
    }

    private fun getCurrentChildName(): String? {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return sharedPreferences.getString("CurrentChildName", null)
    }

    private fun saveCurrentChildName(childName: String) {
        getSharedPreferences("AppPreferences", MODE_PRIVATE)
            .edit()
            .putString("CurrentChildName", childName)
            .apply()
    }

    private fun updateActivePatientBadge(childName: String?) {
        if (childName.isNullOrBlank()) {
            activePatientBadge.visibility = View.GONE
        } else {
            activePatientBadge.text = "Active: $childName"
            activePatientBadge.visibility = View.VISIBLE
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}
