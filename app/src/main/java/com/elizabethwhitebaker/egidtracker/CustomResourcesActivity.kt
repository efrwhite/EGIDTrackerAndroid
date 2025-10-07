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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class CustomResourcesActivity : AppCompatActivity() {

    private lateinit var addResourceButton: Button
    private lateinit var resourceTableLayout: TableLayout

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { Firebase.firestore }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_resources)

        resourceTableLayout = findViewById(R.id.resourceTableLayout)
        addResourceButton = findViewById(R.id.addResourceButton)

        addResourceButton.setOnClickListener {
            startActivity(Intent(this, AddCustomResourceActivity::class.java))
        }

    }

    override fun onResume() {
        super.onResume()
        fetchAndDisplayCustomResources()
    }

    private fun fetchAndDisplayCustomResources() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        resourceTableLayout.removeAllViews()            // ✅ clear previous rows

        Firebase.firestore.collection("CustomResources")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { docs ->
                val rows = docs
                    .map { Triple(it.getString("title") ?: "No Title", it.getString("url") ?: "", it.id) }
                    .sortedBy { it.first.lowercase() }

                rows.forEach { (title, url, id) ->
                    addRowToTable(resourceTableLayout, title, url, id)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load resources: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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

        row.setOnClickListener { openUrl(url) }
        resourceTableLayout.addView(row)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid URL: $url", Toast.LENGTH_SHORT).show()
        }
    }
}
