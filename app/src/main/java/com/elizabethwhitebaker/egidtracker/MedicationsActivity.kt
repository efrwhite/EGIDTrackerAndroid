package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The selected child's current and past (discontinued) medications. */
class MedicationsActivity : AppCompatActivity() {

    companion object {
        /** Medications store their dates in this format. */
        const val MED_DATE_PATTERN = "MM-dd-yyyy"

        /**
         * True once [endDate] is over: a medication is still taken on its end date, so it
         * moves to Past Medications from the day after.
         */
        fun hasEnded(endDate: Date?): Boolean {
            endDate ?: return false
            val startOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.time
            return endDate.before(startOfToday)
        }
    }

    private val storedFormat = SimpleDateFormat(MED_DATE_PATTERN, Locale.US)
    private val displayFormat = SimpleDateFormat(DateField.DISPLAY_PATTERN, Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medications)
        setupPageHeader(R.string.medications)

        findViewById<View>(R.id.addButton).setOnClickListener {
            startActivity(Intent(this, AddMedicationActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadMedications() // also picks up changes made in AddMedicationActivity
    }

    private fun loadMedications() {
        val childId = CurrentChild.getId(this) ?: return
        Firebase.firestore.collection("Medications")
            .whereEqualTo("childId", childId)
            .get()
            .addOnSuccessListener { documents ->
                val current = mutableListOf<Pair<String, ListRow>>()   // sorted by name
                val past = mutableListOf<Pair<Date?, ListRow>>()      // most recently ended first
                for (doc in documents) {
                    val name = doc.getString("medName").orEmpty().ifBlank { "No name" }
                    val end = parse(doc.getString("endDate"))
                    var discontinued = doc.getBoolean("discontinue") ?: false

                    // An active medication whose planned end date is over moves to Past automatically
                    if (!discontinued && hasEnded(end)) {
                        discontinued = true
                        doc.reference.update("discontinue", true)
                    }

                    if (discontinued) {
                        val subtitle = end?.let { getString(R.string.ended_on, displayFormat.format(it)) }
                        past += end to ListRow(name, subtitle, getString(R.string.view)) { open(doc.id) }
                    } else {
                        val subtitle = listOfNotNull(
                            parse(doc.getString("startDate"))?.let { getString(R.string.started_on, displayFormat.format(it)) },
                            end?.let { getString(R.string.ends_on, displayFormat.format(it)) }
                        ).joinToString(" · ")
                        current += name.lowercase() to ListRow(name, subtitle, getString(R.string.edit)) { open(doc.id) }
                    }
                }
                fillList(findViewById<LinearLayout>(R.id.currentRows), findViewById(R.id.currentEmpty),
                    current.sortedBy { it.first }.map { it.second })
                fillList(findViewById<LinearLayout>(R.id.pastRows), findViewById(R.id.pastEmpty),
                    past.sortedByDescending { it.first?.time ?: 0L }.map { it.second })
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load medications: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun parse(date: String?): Date? =
        date?.takeIf { it.isNotBlank() }?.let { runCatching { storedFormat.parse(it) }.getOrNull() }

    private fun open(medicationId: String) {
        startActivity(Intent(this, AddMedicationActivity::class.java).apply {
            putExtra("medicationId", medicationId)
        })
    }
}
