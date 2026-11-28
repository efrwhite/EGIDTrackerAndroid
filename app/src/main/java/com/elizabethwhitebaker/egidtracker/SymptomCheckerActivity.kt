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
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Symptom questionnaire: 20 questions rated 0 (none) to 4 (worst), then 12 yes/no symptoms.
 * The total is the ratings plus one per "yes". Saves, then opens Results.
 *
 * Saved format, unchanged from earlier versions (ReportActivity reads it): `responses` holds
 * the ratings as "0"-"4" followed by "y"/"n" for each symptom, and `symptomDescriptions`
 * the text of each symptom answered yes.
 */
class SymptomCheckerActivity : AppCompatActivity() {

    private val ratedQuestions = listOf(
        R.string.question2, R.string.question3, R.string.question4, R.string.question5,
        R.string.question6, R.string.question7, R.string.question8, R.string.question9,
        R.string.question10, R.string.question11, R.string.question12, R.string.question13,
        R.string.question14, R.string.question15, R.string.question16, R.string.question17,
        R.string.question18, R.string.question19, R.string.question20, R.string.question21
    )

    private val yesNoQuestions = listOf(
        R.string.question23, R.string.question24, R.string.question25, R.string.question26,
        R.string.question27, R.string.question28, R.string.question29, R.string.question30,
        R.string.question31, R.string.question32, R.string.question33, R.string.question34
    )

    private val answerIds = listOf(R.id.answer0, R.id.answer1, R.id.answer2, R.id.answer3, R.id.answer4)

    /** A 0-4 question: its row, and the chosen rating (null until answered). */
    private class RatedQuestion(val row: View, val text: TextView, var answer: Int? = null)

    private val rated = mutableListOf<RatedQuestion>()
    private val yesNo = mutableListOf<SwitchMaterial>()
    private lateinit var dateField: DateField
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_symptom_score_checker)
        setupPageHeader(R.string.symptomScoreChecker, R.string.results) { openResults() }
        if (requireSelectedChild() == null) return // needs a child; offers to add one

        dateField = DateField(this, findViewById(R.id.dateLayout))
        saveButton = findViewById(R.id.saveButton)
        buildQuestions(findViewById(R.id.questions))
        saveButton.setOnClickListener { saveScores() }
    }

    private fun buildQuestions(container: LinearLayout) {
        addHeading(container, R.string.symptoms_rated)
        ratedQuestions.forEach { addRatedQuestion(container, it) }

        addHeading(container, R.string.symptoms_yes_no)
        yesNoQuestions.forEach { text ->
            val switch = layoutInflater.inflate(R.layout.view_symptom_switch, container, false) as SwitchMaterial
            switch.setText(text)
            yesNo += switch
            container.addView(switch)
        }
    }

    private fun addHeading(container: LinearLayout, @StringRes heading: Int) {
        container.addView(TextView(this, null, 0, R.style.Widget_EGIDTracker_PageLabel).apply {
            setText(heading)
            textSize = 17f
            setPadding(0, dp(22), 0, dp(2))
        })
    }

    private fun addRatedQuestion(container: LinearLayout, @StringRes text: Int) {
        val row = layoutInflater.inflate(R.layout.view_qol_question, container, false)
        val question = RatedQuestion(row, row.findViewById<TextView>(R.id.questionText).apply { setText(text) })
        val buttons = answerIds.map { row.findViewById<MaterialButton>(it) }
        buttons.forEachIndexed { value, button ->
            button.setOnClickListener {
                // One answer per question
                buttons.forEach { it.isChecked = it === button }
                question.answer = value
                question.text.setTextColor(ContextCompat.getColor(this, R.color.nearBlack))
            }
        }
        rated += question
        container.addView(row)
    }

    private fun saveScores() {
        val scroll = findViewById<ScrollView>(R.id.symptomScroll)
        if (!dateField.requireDate()) {
            scroll.smoothScrollTo(0, 0)
            return
        }
        val unanswered = rated.filter { it.answer == null }
        if (unanswered.isNotEmpty()) {
            // Mark the unanswered questions and scroll to the first one
            unanswered.forEach { it.text.setTextColor(ContextCompat.getColor(this, R.color.darkSalmon)) }
            val first = unanswered.first().row
            scroll.smoothScrollTo(0, (first.parent as View).top + first.top)
            Toast.makeText(this, R.string.error_answer_all, Toast.LENGTH_SHORT).show()
            return
        }
        val childId = CurrentChild.getId(this) ?: return

        val ratings = rated.map { it.answer!! }
        val data = hashMapOf(
            "totalScore" to ratings.sum() + yesNo.count { it.isChecked },
            "responses" to ratings.map { it.toString() } + yesNo.map { if (it.isChecked) "y" else "n" },
            "symptomDescriptions" to yesNo.filter { it.isChecked }.map { it.text.toString() },
            "date" to Timestamp(dateField.date!!)
        )
        saveButton.isEnabled = false
        // A new document reference (rather than add()) so the write is applied locally right away
        val write = Firebase.firestore.collection("Children").document(childId)
            .collection("Symptom Scores").document().set(data)
        awaitSave(write,
            onSaved = { queued ->
                toastSaved("Answers saved", queued)
                openResults()
                finish()
            },
            onFailed = { e ->
                saveButton.isEnabled = true
                Toast.makeText(this, "Failed to save answers: ${e.message}", Toast.LENGTH_SHORT).show()
            })
    }

    private fun openResults() {
        startActivity(Intent(this, ResultsActivity::class.java).apply {
            putExtra("sourceActivity", "SymptomChecker")
        })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
