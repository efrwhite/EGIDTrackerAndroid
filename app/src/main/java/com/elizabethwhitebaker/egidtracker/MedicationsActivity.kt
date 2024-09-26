package com.elizabethwhitebaker.egidtracker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class MedicationsActivity : AppCompatActivity() {

    private lateinit var addMedButton: Button
    private lateinit var childId: String
    private var isDiscontinued: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medications)

        val medTableLayout: TableLayout = findViewById(R.id.medTableLayout)
        val pastMedTableLayout: TableLayout = findViewById(R.id.pastMedTableLayout)


        //Initialize button
        addMedButton = findViewById(R.id.addButton)

        //Set onlick
        addMedButton.setOnClickListener {
            startActivity(Intent(this, AddMedicationActivity::class.java))
        }

        fetchAndDisplayMedications(medTableLayout, pastMedTableLayout)

    }

    private fun getCurrentChildId(): String? {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("CurrentChildId", null)
    }

    private fun fetchAndDisplayMedications(medTableLayout: TableLayout, pastMedTableLayout: TableLayout) {
        val childId = getCurrentChildId()
        Firebase.firestore.collection("Medications")
            .whereEqualTo("childId", childId)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val medicationName = document.getString("medName") ?: "No Name"
                    val medicationId = document.id
                    val startDate = document.getString("startDate") ?: "None"
                    val endDate = document.getString("endDate") ?: "" // Getting endDate which could be empty
                    var isDiscontinued = document.getBoolean("discontinue") ?: false

                    // If the medication is not discontinued and has a valid end date, check if it should be discontinued
                    if (!isDiscontinued && endDate.isNotEmpty()) {
                        try {
                            val currentDate = LocalDate.now()
                            val formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")
                            val parsedEndDate = LocalDate.parse(endDate, formatter)

                            if (parsedEndDate.isBefore(currentDate)) {
                                // If the endDate is before the current date, mark the medication as discontinued
                                isDiscontinued = true
                                discontinueMedication(medicationId) // Mark as discontinued in Firestore
                            }
                        } catch (e: DateTimeParseException) {
                            // Handle parsing errors if the endDate format is wrong
                            Toast.makeText(
                                this,
                                "Invalid end date format for medication $medicationName",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    // Add rows to the respective table
                    addRowToTable(medTableLayout, pastMedTableLayout, medicationName, medicationId, isDiscontinued, startDate, endDate)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to load current medications: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    @SuppressLint("InflateParams")
    private fun addRowToTable(medTableLayout: TableLayout, pastMedTableLayout: TableLayout, name: String, medicationId: String, isDiscontinued: Boolean, startDate: String, endDate: String) {

        if (isDiscontinued) {
            // Add to past medications list
            val row = layoutInflater.inflate(R.layout.past_med_table_row_item, null)
            val nameTextView = row.findViewById<TextView>(R.id.nameTextView)

            // Show the end date instead of the start date for discontinued medications
            val nameText = if (endDate.isNotEmpty()) {
                "$name\nEnd Date: $endDate"
            } else {
                "$name\nEnd Date: None"  // In case the end date is not set
            }

            nameTextView.text = nameText
            pastMedTableLayout.addView(row)
        } else {
            // Add to current medications list
            val row = layoutInflater.inflate(R.layout.table_row_item, null)
            val nameTextView = row.findViewById<TextView>(R.id.nameTextView)
            val editButton = row.findViewById<Button>(R.id.editButton)

            // Show the start date for active medications
            val nameText = "$name\nStart Date: $startDate"

            nameTextView.text = nameText

            editButton.setOnClickListener {
                navigateToEditActivity(medicationId)
            }

            medTableLayout.addView(row)

        }
    }
    private fun navigateToEditActivity(medicationId: String) {
        val intent = Intent(this, AddMedicationActivity::class.java).apply {
            putExtra("medicationId", medicationId)
            putExtra("editMode", true) // Indicate that we are editing an existing medication
        }
        startActivity(intent)
        finish()
    }

    private fun discontinueMedication(medicationId: String) {
        val medicationRef = Firebase.firestore.collection("Medications").document(medicationId)
        medicationRef.update("discontinue", true)
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to discontinue medication that has past its end date: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}


























