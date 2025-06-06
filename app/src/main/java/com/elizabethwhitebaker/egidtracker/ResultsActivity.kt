package com.elizabethwhitebaker.egidtracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultsActivity : AppCompatActivity() {

    private lateinit var tableLayout: TableLayout
    private lateinit var reportButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        reportButton = findViewById(R.id.genButton)
        val sourceActivity = intent.getStringExtra("sourceActivity") ?: "SymptomChecker"

        reportButton.setOnClickListener {
            val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            val childId = sharedPreferences.getString("CurrentChildId", null) ?: run {
                Toast.makeText(this, "No child selected", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (sourceActivity == "EndoscopyActivity") {
                // Navigate to EndoscopyButtonsActivity
                val intent = Intent(this, EndoscopyButtonsActivity::class.java).apply {
                    putExtra("childId", childId) // Pass child ID for reference
                }
                startActivity(intent)
            } else {
                // Default behavior: navigate to ReportActivity
                val intent = Intent(this, ReportActivity::class.java).apply {
                    putExtra("sourceActivity", sourceActivity)
                }
                startActivity(intent)
            }
        }


        tableLayout = findViewById(R.id.reportTableLayout)

        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val childId = sharedPreferences.getString("CurrentChildId", null) ?: run {
            Toast.makeText(this, "No child selected", Toast.LENGTH_LONG).show()
            finish()  // Close activity if no child ID is available
            return
        }

        loadResults(sourceActivity, childId)
    }

    private fun loadResults(sourceActivity: String, childId: String) {
        val subCollection = when (sourceActivity) {
            "SymptomChecker" -> "Symptom Scores"
            "QoLActivity" -> "Quality of Life Scores"
            "EndoscopyActivity" -> "EndoscopyResults"
            else -> "Symptom Scores"
        }

        Firebase.firestore.collection("Children")
            .document(childId)
            .collection(subCollection)
            .orderBy("date", Query.Direction.ASCENDING)  // Sort by date in chronological order
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val dateTimestamp = document.getTimestamp("date")?.toDate()  // Assuming 'date' is stored as timestamp
                    val score = document.getLong("totalScore")?.toString() ?: "N/A"

                    if (dateTimestamp != null) {
                        if (sourceActivity == "EndoscopyActivity") {
                            addEndoscopyRowToTable(document.id, dateTimestamp, score)
                        } else {
                            addRowToTable(dateTimestamp, score)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun addRowToTable(date: Date, score: String) {
        val row = layoutInflater.inflate(R.layout.score_row_item, tableLayout, false)
        val dateTextView = row.findViewById<TextView>(R.id.date)
        val scoreTextView = row.findViewById<TextView>(R.id.score)

        // Formatting the date
        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
        val formattedDate = dateFormat.format(date)

        dateTextView.text = formattedDate
        scoreTextView.text = score
        tableLayout.addView(row)
    }

    private fun addEndoscopyRowToTable(documentId: String, date: Date, score: String) {
        val row = layoutInflater.inflate(R.layout.endoscopy_score_row, tableLayout, false)
        val dateTextView = row.findViewById<TextView>(R.id.date)
        val scoreTextView = row.findViewById<TextView>(R.id.score)

        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
        val formattedDate = dateFormat.format(date)

        dateTextView.text = formattedDate
        scoreTextView.text = score

        // Make the row clickable
        row.setOnClickListener {
            val intent = Intent(this, EndoscopyActivity::class.java).apply {
                putExtra("isEditing", true)  // Flag for edit mode
                putExtra("reportId", documentId)  // Pass report ID
            }
            startActivity(intent)
        }

        tableLayout.addView(row)
    }

}
