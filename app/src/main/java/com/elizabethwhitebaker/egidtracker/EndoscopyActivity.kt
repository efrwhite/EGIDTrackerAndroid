package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Records an endoscopy's eosinophil counts by site, or edits one (extras: isEditing, reportId).
 * The header's Results link lists past endoscopies.
 */
class EndoscopyActivity : AppCompatActivity() {

    /** Count fields and the Firestore field each is saved as. */
    private val countFields = listOf(
        R.id.upper to "proximate", R.id.middle to "middle", R.id.lower to "lower",
        R.id.stomach to "stomach", R.id.duodenum to "duodenum",
        R.id.rightColon to "rightColon", R.id.middleColon to "middleColon", R.id.leftColon to "leftColon"
    )

    private lateinit var dateField: DateField
    private lateinit var notes: EditText
    private lateinit var saveButton: Button

    private val childId by lazy { CurrentChild.getId(this) }
    private val reportId by lazy { intent.getStringExtra("reportId").takeIf { intent.getBooleanExtra("isEditing", false) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_endoscopy)
        setupPageHeader(R.string.endoscopy_results, R.string.results) {
            val id = childId ?: return@setupPageHeader
            startActivity(Intent(this, ResultsActivity::class.java).apply {
                putExtra("sourceActivity", "EndoscopyActivity")
                putExtra("childId", id)
            })
        }

        dateField = DateField(this, findViewById(R.id.dateLayout))
        notes = findViewById(R.id.notesField)
        saveButton = findViewById(R.id.saveButton)

        reportId?.let { loadReport(it) }
        saveButton.setOnClickListener { saveReport() }
    }

    private fun countOf(id: Int): Int? = findViewById<EditText>(id).text.toString().toIntOrNull()

    private fun saveReport() {
        val date = dateField.date
        if (!dateField.requireDate() || date == null) return
        val child = childId ?: run {
            Toast.makeText(this, "No child selected", Toast.LENGTH_SHORT).show()
            return
        }

        val data = hashMapOf<String, Any?>(
            "totalScore" to countFields.sumOf { (id, _) -> countOf(id) ?: 0 },
            "date" to Timestamp(date),
            "notes" to notes.text.toString().trim()
        )
        countFields.forEach { (id, field) -> data[field] = countOf(id) }

        val results = Firebase.firestore.collection("Children").document(child).collection("EndoscopyResults")
        val ref = reportId?.let { results.document(it) } ?: results.document()
        saveButton.isEnabled = false
        ref.set(data, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Endoscopy results saved", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                saveButton.isEnabled = true
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadReport(id: String) {
        val child = childId ?: return
        Firebase.firestore.collection("Children").document(child).collection("EndoscopyResults").document(id)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener
                countFields.forEach { (viewId, field) ->
                    findViewById<EditText>(viewId).setText(doc.getLong(field)?.toString() ?: "")
                }
                notes.setText(doc.getString("notes"))
                (doc.get("date") as? Timestamp)?.let { dateField.date = it.toDate() }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading report: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
