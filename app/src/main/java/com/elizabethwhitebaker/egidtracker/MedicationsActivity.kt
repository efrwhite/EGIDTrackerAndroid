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
import java.util.Date
import java.util.Locale

/** The selected child's current and past (discontinued) medications. */
class MedicationsActivity : AppCompatActivity() {

    companion object {
        /** Medications store their dates in this format. */
        const val MED_DATE_PATTERN = "MM-dd-yyyy"
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
                val current = mutableListOf<ListRow>()
                val past = mutableListOf<ListRow>()
                for (doc in documents.sortedBy { it.getString("medName")?.lowercase() }) {
                    val name = doc.getString("medName").orEmpty().ifBlank { "No name" }
                    val endDate = doc.getString("endDate").orEmpty()
                    var discontinued = doc.getBoolean("discontinue") ?: false

                    // A medication whose end date has passed is discontinued automatically
                    val end = parse(endDate)
                    if (!discontinued && end != null && end.before(Date())) {
                        discontinued = true
                        doc.reference.update("discontinue", true)
                    }

                    if (discontinued) {
                        val subtitle = parse(endDate)?.let { getString(R.string.ended_on, displayFormat.format(it)) }
                        past += ListRow(name, subtitle, getString(R.string.view)) { open(doc.id) }
                    } else {
                        val subtitle = parse(doc.getString("startDate"))
                            ?.let { getString(R.string.started_on, displayFormat.format(it)) }
                        current += ListRow(name, subtitle, getString(R.string.edit)) { open(doc.id) }
                    }
                }
                fillList(findViewById<LinearLayout>(R.id.currentRows), findViewById(R.id.currentEmpty), current)
                fillList(findViewById<LinearLayout>(R.id.pastRows), findViewById(R.id.pastEmpty), past)
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
