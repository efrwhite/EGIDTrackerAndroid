package com.elizabethwhitebaker.egidtracker

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class EndoscopyActivity : AppCompatActivity() {

    private lateinit var resultsButton: Button
    private lateinit var dateInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_endoscopy)

        dateInput = findViewById(R.id.endoscopyDate)
        dateInput.setOnClickListener{
            showDatePickerDialog()
        }

        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            saveEndoscopyData()
        }

        resultsButton = findViewById(R.id.resultsButton)
        resultsButton.setOnClickListener {
            val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            val intent = Intent(this, ResultsActivity::class.java).apply {
                putExtra("sourceActivity", "EndoscopyActivity")
            }
            startActivity(intent)
            finish()
        }

    }


    // Ensure all fields are filled and numeric
    private fun areAllFieldsFilled(): Boolean {
        val fieldIds = arrayOf(
            R.id.upper, R.id.middle, R.id.lower,
            R.id.stomach, R.id.duodenum, R.id.rightColon,
            R.id.middleColon, R.id.leftColon
        )
        for (id in fieldIds) {
            val editText = findViewById<EditText>(id)
            val inputText = editText.text.toString().trim()
            if(inputText.toIntOrNull() == null) {
                return false
            }
        }
        return true
    }


    private fun saveEndoscopyData() {
        if (!areAllFieldsFilled()) {
            Toast.makeText(this, "Please enter numeric values only.", Toast.LENGTH_SHORT).show()
            return
        }

        val totalScore = calculateTotalScore()
        val dateString = dateInput.text.toString()

        if (dateString.isBlank()) {
            Toast.makeText(this, "Please enter a valid date.", Toast.LENGTH_SHORT).show()
            return
        }

        saveResultsToFirestore(totalScore, dateString)

        // After saving, go back to the home activity
        val intent = Intent(this, PlanActivity::class.java)
        startActivity(intent)
        finish()
    }


    private fun calculateTotalScore(): Int {
        var score = 0
        val fieldIds = arrayOf(
            R.id.upper, R.id.middle, R.id.lower,
            R.id.stomach, R.id.duodenum, R.id.rightColon,
            R.id.middleColon, R.id.leftColon
        )
        for (id in fieldIds) {
            val editText = findViewById<EditText>(id)
            score += editText.text.toString().toIntOrNull() ?: 0
        }
        return score
    }


    private fun saveResultsToFirestore(totalScore: Int, date: String) {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val childId = sharedPreferences.getString("CurrentChildId", null) ?: run {
            Toast.makeText(this, "No child selected", Toast.LENGTH_SHORT).show()
            return
        }

        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        val dateObject = dateFormat.parse(date) ?: run {
            Toast.makeText(this, "Invalid date format.", Toast.LENGTH_SHORT).show()
            return
        }
        val timestamp = com.google.firebase.Timestamp(dateObject)

        val data = hashMapOf(
            "totalScore" to totalScore,
            "proximate" to findViewById<EditText>(R.id.upper).text.toString().toIntOrNull(),
            "middle" to findViewById<EditText>(R.id.middle).text.toString().toIntOrNull(),
            "lower" to findViewById<EditText>(R.id.lower).text.toString().toIntOrNull(),
            "stomach" to findViewById<EditText>(R.id.stomach).text.toString().toIntOrNull(),
            "duodenum" to findViewById<EditText>(R.id.duodenum).text.toString().toIntOrNull(),
            "rightColon" to findViewById<EditText>(R.id.rightColon).text.toString().toIntOrNull(),
            "middleColon" to findViewById<EditText>(R.id.middleColon).text.toString().toIntOrNull(),
            "leftColon" to findViewById<EditText>(R.id.leftColon).text.toString().toIntOrNull(),
            "date" to timestamp
        )

        Firebase.firestore.collection("Children").document(childId)
            .collection("EndoscopyResults").add(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Endoscopy results saved successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = "${selectedMonth + 1}/$selectedDay/$selectedYear"
                dateInput.setText(formattedDate)
            },
            year, month, day
        )
        datePickerDialog.show()
    }


}























