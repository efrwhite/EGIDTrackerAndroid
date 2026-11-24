package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Past scores for the selected child, optionally within a date range, updating live. Shared by
 * the Symptom Checker, Quality of Life and Endoscopy screens (extra: sourceActivity).
 */
class ResultsActivity : AppCompatActivity() {

    private lateinit var startDate: DateField
    private lateinit var endDate: DateField
    private var resultsListener: ListenerRegistration? = null

    private val displayFormat = SimpleDateFormat(DateField.DISPLAY_PATTERN, Locale.US)
    private val sourceActivity by lazy { intent.getStringExtra("sourceActivity") ?: "SymptomChecker" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)
        setupPageHeader(R.string.results)

        val childId = CurrentChild.getId(this) ?: run {
            Toast.makeText(this, "No child selected", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        startDate = DateField(this, findViewById(R.id.startDateLayout))
        endDate = DateField(this, findViewById(R.id.endDateLayout))
        startDate.onPicked = { attachResultsListener(childId) }
        endDate.onPicked = { attachResultsListener(childId) }

        findViewById<View>(R.id.genButton).setOnClickListener {
            val target = if (sourceActivity == "EndoscopyActivity") EndoscopyButtonsActivity::class.java
                else ReportActivity::class.java
            startActivity(Intent(this, target).apply {
                putExtra("sourceActivity", sourceActivity)
                putExtra("childId", childId)
                startDate.date?.let { putExtra("startDateMillis", it.time) }
                endDate.date?.let { putExtra("endDateMillis", it.time) }
            })
        }

        attachResultsListener(childId)
    }

    /** Listens to the scores in the chosen range; re-run whenever a date changes. */
    private fun attachResultsListener(childId: String) {
        resultsListener?.remove()

        val subCollection = when (sourceActivity) {
            "QoLActivity" -> "Quality of Life Scores"
            "EndoscopyActivity" -> "EndoscopyResults"
            else -> "Symptom Scores"
        }
        var query: Query = Firebase.firestore.collection("Children").document(childId)
            .collection(subCollection)
            .orderBy("date", Query.Direction.ASCENDING)
        startDate.date?.let { query = query.whereGreaterThanOrEqualTo("date", Timestamp(it)) }
        endDate.date?.let { end ->
            // Inclusive through the end of that day
            val endOfDay = Calendar.getInstance().apply {
                time = end
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }.time
            query = query.whereLessThanOrEqualTo("date", Timestamp(endOfDay))
        }

        resultsListener = query.addSnapshotListener { snapshot, error ->
            showError(error)
            if (error != null) return@addSnapshotListener
            val rows = snapshot?.documents.orEmpty().mapNotNull { doc ->
                val date = (doc.get("date") as? Timestamp)?.toDate() ?: return@mapNotNull null
                val score = doc.getLong("totalScore")?.toString() ?: "N/A"
                // Endoscopy results open for editing; questionnaire scores are display-only
                if (sourceActivity == "EndoscopyActivity") {
                    ListRow(displayFormat.format(date), action = score) { editEndoscopy(doc.id) }
                } else {
                    ListRow(displayFormat.format(date), action = score)
                }
            }
            fillList(findViewById<LinearLayout>(R.id.scoreRows), findViewById(R.id.scoresEmpty), rows)
        }
    }

    private fun showError(error: FirebaseFirestoreException?) {
        findViewById<View>(R.id.errorBanner).visibility = if (error == null) View.GONE else View.VISIBLE
        if (error != null) {
            findViewById<TextView>(R.id.errorMessage).setText(
                if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) R.string.scores_permission_error
                else R.string.scores_network_error
            )
        }
    }

    private fun editEndoscopy(reportId: String) {
        startActivity(Intent(this, EndoscopyActivity::class.java).apply {
            putExtra("isEditing", true)
            putExtra("reportId", reportId)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        resultsListener?.remove()
    }
}
