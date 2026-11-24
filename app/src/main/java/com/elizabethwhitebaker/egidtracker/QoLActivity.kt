package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Quality-of-life questionnaire: 31 questions in 7 sections, each answered 0 (never a
 * problem) to 4 (almost always). Saves the answers and their total, then opens Results.
 */
class QoLActivity : AppCompatActivity() {

    /** Section heading and its questions, in the order they're asked (and saved). */
    private val sections: List<Pair<Int, List<Int>>> = listOf(
        R.string.SymptomsI to listOf(R.string.symptoms1, R.string.symptoms2, R.string.symptoms3,
            R.string.symptoms4, R.string.symptoms5, R.string.symptoms6),
        R.string.SymptomsII to listOf(R.string.symptoms7, R.string.symptoms8, R.string.symptoms9, R.string.symptoms10),
        R.string.Treatment to listOf(R.string.treatment1, R.string.treatment2, R.string.treatment3,
            R.string.treatment4, R.string.treatment5),
        R.string.Worry to listOf(R.string.worry1, R.string.worry2, R.string.worry3, R.string.worry4),
        R.string.Communication to listOf(R.string.comm1, R.string.comm2, R.string.comm3, R.string.comm4, R.string.comm5),
        R.string.FoodandEating to listOf(R.string.FandE1, R.string.FandE2, R.string.FandE3, R.string.FandE4),
        R.string.FoodFeelings to listOf(R.string.feelings1, R.string.feelings2, R.string.feelings3),
    )

    private val answerIds = listOf(R.id.answer0, R.id.answer1, R.id.answer2, R.id.answer3, R.id.answer4)

    /** One entry per question: its row, and the chosen answer (null until answered). */
    private class Question(val row: View, val text: TextView, var answer: Int? = null)

    private val questions = mutableListOf<Question>()
    private lateinit var dateField: DateField
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qol_score_checker)
        setupPageHeader(R.string.qol_title, R.string.results) { openResults() }

        dateField = DateField(this, findViewById(R.id.dateLayout))
        saveButton = findViewById(R.id.saveButton)
        buildQuestions(findViewById(R.id.questions))
        saveButton.setOnClickListener { saveScores() }
    }

    private fun buildQuestions(container: LinearLayout) {
        sections.forEach { (heading, items) ->
            container.addView(TextView(this, null, 0, R.style.Widget_EGIDTracker_PageLabel).apply {
                setText(heading)
                textSize = 17f
                setPadding(0, dp(22), 0, dp(2))
            })
            items.forEach { addQuestion(container, it) }
        }
    }

    private fun addQuestion(container: LinearLayout, @StringRes text: Int) {
        val row = layoutInflater.inflate(R.layout.view_qol_question, container, false)
        val question = Question(row, row.findViewById<TextView>(R.id.questionText).apply { setText(text) })
        val buttons = answerIds.map { row.findViewById<MaterialButton>(it) }
        buttons.forEachIndexed { value, button ->
            button.setOnClickListener {
                // One answer per question
                buttons.forEach { it.isChecked = it === button }
                question.answer = value
                question.text.setTextColor(ContextCompat.getColor(this, R.color.nearBlack))
            }
        }
        questions += question
        container.addView(row)
    }

    private fun saveScores() {
        if (!dateField.requireDate()) {
            findViewById<ScrollView>(R.id.qolScroll).smoothScrollTo(0, 0)
            return
        }
        val unanswered = questions.filter { it.answer == null }
        if (unanswered.isNotEmpty()) {
            // Mark the unanswered questions and scroll to the first one
            unanswered.forEach { it.text.setTextColor(ContextCompat.getColor(this, R.color.darkSalmon)) }
            val first = unanswered.first().row
            findViewById<ScrollView>(R.id.qolScroll).smoothScrollTo(0, (first.parent as View).top + first.top)
            Toast.makeText(this, R.string.error_answer_all, Toast.LENGTH_SHORT).show()
            return
        }
        val childId = CurrentChild.getId(this) ?: run {
            Toast.makeText(this, "No child selected", Toast.LENGTH_SHORT).show()
            return
        }

        val responses = questions.map { it.answer!! }
        val data = hashMapOf(
            "totalScore" to responses.sum(),
            "responses" to responses,
            "date" to Timestamp(dateField.date!!)
        )
        saveButton.isEnabled = false
        Firebase.firestore.collection("Children").document(childId)
            .collection("Quality of Life Scores").add(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Answers saved", Toast.LENGTH_SHORT).show()
                openResults()
                finish()
            }
            .addOnFailureListener { e ->
                saveButton.isEnabled = true
                Toast.makeText(this, "Failed to save answers: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openResults() {
        startActivity(Intent(this, ResultsActivity::class.java).apply {
            putExtra("sourceActivity", "QoLActivity")
        })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
