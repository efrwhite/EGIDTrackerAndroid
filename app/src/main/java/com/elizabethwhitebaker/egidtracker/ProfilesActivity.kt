package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ProfilesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profiles)

        val addChildLink: Button = findViewById(R.id.addChildlink)
        val addCaregiverLink: Button = findViewById(R.id.addCaregiverlink)
        val childrenTableLayout: TableLayout = findViewById(R.id.childTableLayout)
        val caregiversTableLayout: TableLayout = findViewById(R.id.caregiverTableLayout)

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
        Firebase.firestore.collection("Children")
            .whereEqualTo("parentUserId", userId)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val childName = document.getString("firstName") ?: "No Name"
                    val childId = document.id
                    addRowToTable(table, childId, childName, true) // true for children
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
                    addRowToTable(table, caregiverId, caregiverName, false) // false for caregivers
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
            handleChildNameClick(id, isChild)
        }

        editButton.setOnClickListener {
            navigateToEditActivity(id, isChild)
        }

        table.addView(row)
    }

    private fun handleChildNameClick(childId: String, isChild: Boolean) {
        if (isChild) {
            // Save the selected child ID and navigate to HomeActivity if it changes
            val currentChildId = getCurrentChildId()
            if (childId != currentChildId) {
                saveCurrentChildId(childId)
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
                putExtra("editMode", true) // Indicate that we are editing an existing entry
            }
        } else {
            Intent(this, AddCaregiverActivity::class.java).apply {
                putExtra("caregiverId", id)
                putExtra("editMode", true) // Indicate that we are editing an existing entry
            }
        }
        startActivity(intent)
    }

    private fun getCurrentChildId(): String? {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return sharedPreferences.getString("CurrentChildId", null)
    }

    private fun saveCurrentChildId(childId: String) {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE).edit()
        sharedPreferences.putString("CurrentChildId", childId)
        sharedPreferences.apply()
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}
