package com.elizabethwhitebaker.egidtracker

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class QoLActivity : AppCompatActivity() {

    private lateinit var resultsButton: Button
    private lateinit var visitDateInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qol_score_checker)

        visitDateInput = findViewById(R.id.visitDateInput)
        visitDateInput.setOnClickListener {
            showDatePickerDialog()
        }

        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            saveScores()
        }
        resultsButton = findViewById(R.id.resultsButton)

        resultsButton.setOnClickListener {
            val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            val intent = Intent(this, ResultsActivity::class.java).apply {
                putExtra("sourceActivity", "QoLActivity")
            }
            startActivity(intent)
            finish()
        }
    }

    private fun areAllFieldsFilled(): Boolean {
        val numberOfQuestions = 31
        for (i in 1..numberOfQuestions) {
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val answer = findViewById<EditText>(answerId)
            if (answer.text.toString().trim().isEmpty()) {
                return false // Found an empty field, return false
            }
        }
        return true // All fields are filled
    }
    private fun saveScores() {
        if (!areAllFieldsFilled()) {
            Toast.makeText(this, "Please fill out all fields before saving.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!validateInputs()) {
            return // Stop further execution if validation fails
        }

        val totalScore = calculateTotalScore()
        val responses = collectResponses()
        val dateInput: EditText = findViewById(R.id.visitDateInput)
        val dateString = dateInput.text.toString().trim()

        if (dateString.isBlank()) {
            Toast.makeText(this, "Please enter a valid date.", Toast.LENGTH_SHORT).show()
            return
        }

        saveResultsToFirestore(totalScore, responses, dateString)

        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val intent = Intent(this, ResultsActivity::class.java).apply {
            putExtra("sourceActivity", "QoLActivity")
        }
        startActivity(intent)
        finish()
    }

    private fun validateInputs(): Boolean {
        val numberOfQuestions = 31  // Adjust according to your questions

        for (i in 1..numberOfQuestions) {
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val answer = findViewById<EditText>(answerId)
            val input = answer.text.toString().trim() // Trim input

            // Check if input is a valid number between 0 and 4
            val number = input.toIntOrNull()
            if (number == null || number !in 0..4) {
                answer.error = "Enter a number between 0 and 4"
                Toast.makeText(this, "Question $i: Enter a number between 0 and 4", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true // All inputs are valid
    }


    private fun calculateTotalScore(): Int {
        var score = 0
        val numberOfQuestions = 31  // Assuming you have 31 questions indexed from 1 to 31
        for (i in 1..numberOfQuestions) {
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val answer = findViewById<EditText>(answerId)
            score += answer.text.toString().toIntOrNull() ?: 0
        }
        return score
    }

    private fun collectResponses(): List<Int> {
        val responses = mutableListOf<Int>()
        val numberOfQuestions = 31  // Adjust this based on your actual number of questions
        for (i in 1..numberOfQuestions) {
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val answer = findViewById<EditText>(answerId)
            responses.add(answer.text.toString().toIntOrNull() ?: 0)
        }
        return responses
    }

    private fun saveResultsToFirestore(totalScore: Int, responses: List<Int>, date: String) {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val childId = sharedPreferences.getString("CurrentChildId", null) ?: run {
            Toast.makeText(this, "No child selected", Toast.LENGTH_SHORT).show()
            return
        }

        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        val dateObject = dateFormat.parse(date) // Parse the date string into a Date object
        val timestamp = dateObject?.let { Timestamp(it) } ?: run {  // Convert to Timestamp
            Toast.makeText(this, "Invalid date format.", Toast.LENGTH_SHORT).show()
            return
        }

        val data = hashMapOf(
            "totalScore" to totalScore,
            "responses" to responses,
            "date" to timestamp
        )

        Firebase.firestore.collection("Children").document(childId)
            .collection("Quality of Life Scores").add(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Score saved successfully!", Toast.LENGTH_SHORT).show()
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
            { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
                // Format the date and set it to the EditText
                val formattedDate = "${selectedMonth + 1}/$selectedDay/$selectedYear"
                visitDateInput.setText(formattedDate)
            },
            year, month, day
        )

        datePickerDialog.show()
    }

}
