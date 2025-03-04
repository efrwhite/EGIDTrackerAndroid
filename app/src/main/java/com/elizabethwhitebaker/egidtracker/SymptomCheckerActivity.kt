package com.elizabethwhitebaker.egidtracker

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SymptomCheckerActivity : AppCompatActivity() {

    private lateinit var resultsButton: Button
    private lateinit var visitDateInput: EditText
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_symptom_score_checker)

        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            saveScores()
        }

        visitDateInput = findViewById(R.id.visitDateInput)
        visitDateInput.setOnClickListener {
            showDatePickerDialog()
        }

        resultsButton = findViewById(R.id.resultsButton)

        resultsButton.setOnClickListener {
            val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            val intent = Intent(this, ResultsActivity::class.java).apply {
                putExtra("sourceActivity", "SymptomChecker")
            }
            startActivity(intent)
        }

    }

    private fun areAllFieldsFilled(): Boolean {
        val numberOfQuestions = 32
        for (i in 1..numberOfQuestions) {
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val view = findViewById<View>(answerId)
            // If it's an EditText, ensure it's not empty.
            if (view is EditText && view.text.toString().trim().isEmpty()) {
                return false
            }
            // For a SwitchCompat, we assume it always has a value.
        }
        return true
    }

    private fun saveScores() {

        if (!areAllFieldsFilled()) {
            Toast.makeText(this, "Please fill out all fields before saving.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!validateInputs()) {
            return  // Stop further execution if validation fails
        }

        val totalScore = calculateTotalScore()
        val responses = collectResponses()
        val symptomDescriptions = collectSymptomDescriptions()
        val dateInput: EditText = findViewById(R.id.visitDateInput)
        val dateString = dateInput.text.toString()

        saveResultsToFirestore(totalScore, responses, symptomDescriptions, dateString)

        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val intent = Intent(this, ResultsActivity::class.java).apply {
            putExtra("sourceActivity", "SymptomChecker")
        }
        startActivity(intent)
        finish()
    }

    private fun validateInputs(): Boolean {
        for (i in 1..20) { // Validate numerical score questions (0-4)
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val answer = findViewById<EditText>(answerId)
            val input = answer.text.toString().trim()  // Trim the input

            // Check if input is a valid number between 0 and 4
            val number = input.toIntOrNull()
            if (number == null || number !in 0..4) {
                answer.error = "Enter a number between 0 and 4"
                Toast.makeText(this, "Question $i: Enter a number between 0 and 4", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        // No need once switched to radio buttons
        /**
        for (i in 21..32) { // Validate Yes/No questions (Y/N)
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val answer = findViewById<EditText>(answerId)
            val input = answer.text.toString().trim()  // Trim the input

            // Check if input is 'Y', 'y', 'N', or 'n'
            if (input.isNotEmpty() && !input.equals("y", true) && !input.equals("n", true)) {
                answer.error = "Enter 'Y' or 'N'"
                Toast.makeText(this, "Question $i: Enter 'Y' or 'N'", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        */
        return true // All inputs are valid
    }




    private fun calculateTotalScore(): Int {
        var score = 0
        for (i in 1..20) { // IDs for numerical score questions
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val answer = findViewById<EditText>(answerId)
            score += answer.text.toString().toIntOrNull() ?: 0
        }
        // Sum yes/no answers (IDs 21–32): add 1 point for a "y"
        for (i in 21..32) {
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val view = findViewById<View>(answerId)
            val answerStr = when (view) {
                is EditText -> view.text.toString().trim()
                is SwitchCompat -> if (view.isChecked) "y" else "n"
                else -> ""
            }
            if (answerStr.equals("y", ignoreCase = true)) {
                score += 1
            }
        }
        return score
    }

    private fun collectResponses(): List<String> {
        val responses = mutableListOf<String>()
        for (i in 1..32) {
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val view = findViewById<View>(answerId)
            val response = when (view) {
                is EditText -> view.text.toString().trim()
                is SwitchCompat -> if (view.isChecked) "y" else "n"
                else -> ""
            }
            responses.add(response)
        }
        return responses
    }


    private fun collectSymptomDescriptions(): List<String> {
        val descriptions = mutableListOf<String>()
        for (i in 21..32) {
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val view = findViewById<View>(answerId)
            val answerStr = when (view) {
                is EditText -> view.text.toString().trim()
                is SwitchCompat -> if (view.isChecked) "y" else "n"
                else -> ""
            }
            if (answerStr.equals("y", ignoreCase = true)) {
                val questionId = resources.getIdentifier("question$i", "id", packageName)
                val question = findViewById<TextView>(questionId)
                descriptions.add(question.text.toString())
            }
        }
        return descriptions
    }


    private fun saveResultsToFirestore(totalScore: Int, responses: List<String>, symptoms: List<String>, date: String) {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val childId = sharedPreferences.getString("CurrentChildId", null) ?: run {
            Toast.makeText(this, "No child selected", Toast.LENGTH_SHORT).show()
            return
        }

        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        val dateObject = dateFormat.parse(date) // Parse the date string into a Date object
        val timestamp = dateObject?.let { Timestamp(it) } ?: Timestamp.now() // Convert to Timestamp or use current timestamp if parsing fails

        val data = hashMapOf(
            "totalScore" to totalScore,
            "responses" to responses,
            "symptomDescriptions" to symptoms,
            "date" to timestamp
        )

        Firebase.firestore.collection("Children").document(childId).collection("Symptom Scores")
            .add(data)
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
