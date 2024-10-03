package com.elizabethwhitebaker.egidtracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class CustomResourcesActivity : AppCompatActivity() {

    private lateinit var addResourceButton: Button
    private lateinit var resourceTableLayout: TableLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_resources)

        // Initialize the UI components
        resourceTableLayout = findViewById(R.id.resourceTableLayout)
        addResourceButton = findViewById(R.id.addResourceButton)

        // Set the onClickListener for the Add button
        addResourceButton.setOnClickListener {
            val intent = Intent(this, AddCustomResourceActivity::class.java)
            startActivity(intent)
        }

        // Fetch and display custom resources based on the current username
        fetchAndDisplayCustomResources()
    }

    private fun fetchAndDisplayCustomResources() {
        val username = getCurrentUsername()
        if (username != null) {
            Firebase.firestore.collection("CustomResources")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener { documents ->
                    val resources = mutableListOf<Map<String, Any>>()

                    // Collect resource data
                    for (document in documents) {
                        val resourceTitle = document.getString("title") ?: "No Title"
                        val resourceUrl = document.getString("url") ?: "No URL"
                        val resourceId = document.id

                        val resourceData = mapOf(
                            "title" to resourceTitle,
                            "url" to resourceUrl,
                            "resourceId" to resourceId
                        )
                        resources.add(resourceData)
                    }

                    // Sort and display resources
                    resources.sortBy { it["title"].toString() }
                    resources.forEach { resource ->
                        addRowToTable(
                            resourceTableLayout,
                            resource["title"] as String,
                            resource["url"] as String,
                            resource["resourceId"] as String
                        )
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to load resources: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "No username found. Please log in.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentUsername(): String? {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("CurrentUsername", null)
    }

    private fun addRowToTable(resourceTableLayout: TableLayout, title: String, url: String, resourceId: String) {
        val row = layoutInflater.inflate(R.layout.activity_custom_resource_row, null)
        val resourceTitleTextView = row.findViewById<TextView>(R.id.resourceTitle)
        val editButton = row.findViewById<Button>(R.id.editButton)

        resourceTitleTextView.text = title

        editButton.setOnClickListener {
            val intent = Intent(this, AddCustomResourceActivity::class.java).apply {
                putExtra("resourceId", resourceId)
                putExtra("isEditMode", true)
            }
            startActivity(intent)
        }

        row.setOnClickListener {
            openUrl(url)
        }

        resourceTableLayout.addView(row)
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid URL: $url", Toast.LENGTH_SHORT).show()
        }
    }
}
