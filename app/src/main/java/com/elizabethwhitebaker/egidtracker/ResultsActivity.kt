package com.elizabethwhitebaker.egidtracker

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ResultsActivity : AppCompatActivity() {

    private lateinit var tableLayout: TableLayout
    private lateinit var reportButton: Button

    // NEW: date range UI
    private lateinit var startDateButton: Button
    private lateinit var endDateButton: Button

    // NEW: store selected date range
    private var startDateMillis: Long? = null
    private var endDateMillis: Long? = null

    // NEW: realtime listener
    private var resultsListener: ListenerRegistration? = null

    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        reportButton = findViewById(R.id.genButton)
        tableLayout = findViewById(R.id.reportTableLayout)

        // NEW: hook up date buttons (from your updated XML)
        startDateButton = findViewById(R.id.startDateButton)
        endDateButton = findViewById(R.id.endDateButton)

        val sourceActivity = intent.getStringExtra("sourceActivity") ?: "SymptomChecker"

        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val childId = sharedPreferences.getString("CurrentChildId", null) ?: run {
            Toast.makeText(this, "No child selected", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // NEW: Date pickers update the list immediately
        startDateButton.setOnClickListener {
            showDatePicker { millis ->
                startDateMillis = millis
                startDateButton.text = dateFormat.format(Date(millis))
                attachResultsListener(sourceActivity, childId)
            }
        }

        endDateButton.setOnClickListener {
            showDatePicker { millis ->
                endDateMillis = millis
                endDateButton.text = dateFormat.format(Date(millis))
                attachResultsListener(sourceActivity, childId)
            }
        }

        reportButton.setOnClickListener {
            if (sourceActivity == "EndoscopyActivity") {
                val intent = Intent(this, EndoscopyButtonsActivity::class.java).apply {
                    putExtra("childId", childId)
                    startDateMillis?.let { putExtra("startDateMillis", it) }
                    endDateMillis?.let { putExtra("endDateMillis", it) }
                }
                startActivity(intent)
            } else {
                val intent = Intent(this, ReportActivity::class.java).apply {
                    putExtra("sourceActivity", sourceActivity)
                    startDateMillis?.let { putExtra("startDateMillis", it) }
                    endDateMillis?.let { putExtra("endDateMillis", it) }
                }
                startActivity(intent)
            }
        }

        // Initial load (realtime)
        attachResultsListener(sourceActivity, childId)
    }

    // NEW: realtime listener that rebuilds the query each time dates change
    private fun attachResultsListener(sourceActivity: String, childId: String) {
        resultsListener?.remove()
        resultsListener = null

        val subCollection = when (sourceActivity) {
            "SymptomChecker" -> "Symptom Scores"
            "QoLActivity" -> "Quality of Life Scores"
            "EndoscopyActivity" -> "EndoscopyResults"
            else -> "Symptom Scores"
        }

        var query: Query = Firebase.firestore.collection("Children")
            .document(childId)
            .collection(subCollection)
            .orderBy("date", Query.Direction.ASCENDING)

        val start = startDateMillis
        val end = endDateMillis

        if (start != null) {
            query = query.whereGreaterThanOrEqualTo("date", Timestamp(Date(start)))
        }

        if (end != null) {
            // inclusive through end-of-day
            val endInclusiveMillis = Calendar.getInstance().apply {
                timeInMillis = end
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            query = query.whereLessThanOrEqualTo("date", Timestamp(Date(endInclusiveMillis)))
        }

        resultsListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Toast.makeText(this, "Failed to load data: ${error.message}", Toast.LENGTH_LONG).show()
                return@addSnapshotListener
            }

            tableLayout.removeAllViews()

            val documents = snapshot?.documents ?: emptyList()
            for (document in documents) {
                val dateTimestamp = document.getTimestamp("date")?.toDate()
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
    }

    private fun showDatePicker(onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val pickedMillis = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    // normalize start of day
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                onPicked(pickedMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun addRowToTable(date: Date, score: String) {
        val row = layoutInflater.inflate(R.layout.score_row_item, tableLayout, false)
        val dateTextView = row.findViewById<TextView>(R.id.date)
        val scoreTextView = row.findViewById<TextView>(R.id.score)

        val formattedDate = dateFormat.format(date)

        dateTextView.text = formattedDate
        scoreTextView.text = score
        tableLayout.addView(row)
    }

    private fun addEndoscopyRowToTable(documentId: String, date: Date, score: String) {
        val row = layoutInflater.inflate(R.layout.endoscopy_score_row, tableLayout, false)
        val dateTextView = row.findViewById<TextView>(R.id.date)
        val scoreTextView = row.findViewById<TextView>(R.id.score)

        val formattedDate = dateFormat.format(date)

        dateTextView.text = formattedDate
        scoreTextView.text = score

        row.setOnClickListener {
            val intent = Intent(this, EndoscopyActivity::class.java).apply {
                putExtra("isEditing", true)
                putExtra("reportId", documentId)
            }
            startActivity(intent)
        }

        tableLayout.addView(row)
    }

    override fun onDestroy() {
        super.onDestroy()
        resultsListener?.remove()
        resultsListener = null
    }
}
