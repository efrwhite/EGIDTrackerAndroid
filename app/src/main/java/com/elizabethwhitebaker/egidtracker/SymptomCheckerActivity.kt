package com.elizabethwhitebaker.egidtracker

import android.content.Context
import android.os.Bundle
import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

class SymptomCheckerActivity : AppCompatActivity() {

    private lateinit var resultsButton: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_symptom_score_checker)

        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            saveScores()
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

        val totalScore = calculateTotalScore()
        val responses = collectResponses()
        val symptomDescriptions = collectSymptomDescriptions()
        val dateInput: EditText = findViewById(R.id.visitDateInput)
        val dateString = dateInput.text.toString()

        saveResultsToFirestore(totalScore, responses, symptomDescriptions, dateString)
    }

    private fun calculateTotalScore(): Int {
        var score = 0
        for (i in 1..20) { // IDs for numerical score questions
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val answer = findViewById<EditText>(answerId)
            score += answer.text.toString().toIntOrNull() ?: 0
        }
        for (i in 21..32) { // IDs for Y/N questions
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val answer = findViewById<EditText>(answerId)
            score += if (answer.text.toString().equals("y", ignoreCase = true)) 1 else 0
        }
        return score
    }

    private fun collectResponses(): List<String> {
        val responses = mutableListOf<String>()
        for (i in 1..32) {
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val answer = findViewById<EditText>(answerId)
            responses.add(answer.text.toString())
        }
        return responses
    }

    private fun collectSymptomDescriptions(): List<String> {
        val descriptions = mutableListOf<String>()
        for (i in 21..32) { // Assuming IDs 22 to 33 are for Y/N questions
            val answerId = resources.getIdentifier("answer$i", "id", packageName)
            val answer = findViewById<EditText>(answerId)
            if (answer.text.toString().equals("y", ignoreCase = true)) {
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
}
