package com.elizabethwhitebaker.egidtracker

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Adds a medication, or edits one (extra: medicationId). A discontinued medication opens
 * read-only except for its notes.
 */
class AddMedicationActivity : AppCompatActivity() {

    private lateinit var medNameLayout: TextInputLayout
    private lateinit var medName: EditText
    private lateinit var dosage: EditText
    private lateinit var notes: EditText
    private lateinit var discontinue: SwitchMaterial
    private lateinit var startDate: DateField
    private lateinit var endDate: DateField
    private lateinit var saveButton: Button

    private val childId by lazy { CurrentChild.getId(this) }
    private val medicationId by lazy { intent.getStringExtra("medicationId") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_addmedication)
        setupPageHeader(if (medicationId == null) R.string.add_medications else R.string.edit_medication)

        medNameLayout = findViewById(R.id.medNameLayout)
        medName = findViewById(R.id.medNameField)
        dosage = findViewById(R.id.medAmountField)
        notes = findViewById(R.id.medNotesField)
        discontinue = findViewById(R.id.discontinueSwitch)
        saveButton = findViewById(R.id.medSaveButton)
        startDate = DateField(this, findViewById(R.id.startDateLayout), MedicationsActivity.MED_DATE_PATTERN)
        // The end date may be planned ahead; MedicationsActivity discontinues it once it passes
        endDate = DateField(this, findViewById(R.id.endDateLayout), MedicationsActivity.MED_DATE_PATTERN, allowFuture = true)

        medName.doAfterTextChanged { medNameLayout.error = null }
        discontinue.setOnCheckedChangeListener { _, checked ->
            findViewById<View>(R.id.endDateGroup).visibility = if (checked) View.VISIBLE else View.GONE
        }

        medicationId?.let { loadMedication(it) }
        saveButton.setOnClickListener { saveMedication() }
    }

    private fun loadMedication(id: String) {
        Firebase.firestore.collection("Medications").document(id).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Medication not found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                medName.setText(doc.getString("medName"))
                dosage.setText(doc.getString("dosage"))
                notes.setText(doc.getString("notes"))
                startDate.stored = doc.getString("startDate").orEmpty()
                endDate.stored = doc.getString("endDate").orEmpty()
                discontinue.isChecked = doc.getBoolean("discontinue") ?: false

                // A discontinued medication is history: only its notes can still change
                if (discontinue.isChecked) {
                    setupPageHeader(R.string.view_medication)
                    medNameLayout.isEnabled = false
                    findViewById<TextInputLayout>(R.id.dosageLayout).isEnabled = false
                    startDate.isEnabled = false
                    endDate.isEnabled = false
                    discontinue.isEnabled = false
                    saveButton.setText(R.string.save_notes)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load medication: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveMedication() {
        val name = medName.text.toString().trim()
        val discontinued = discontinue.isChecked

        var valid = true
        if (name.isEmpty()) { medNameLayout.error = getString(R.string.error_required); valid = false }
        if (!startDate.requireDate()) valid = false
        if (discontinued) {
            if (!endDate.requireDate()) valid = false
            else if (startDate.date != null && !endDate.date!!.after(startDate.date)) {
                findViewById<TextInputLayout>(R.id.endDateLayout).error = getString(R.string.error_end_before_start)
                valid = false
            }
        }
        if (!valid) return

        val medicationMap = hashMapOf<String, Any?>(
            "medName" to name,
            "dosage" to dosage.text.toString().trim(),
            "startDate" to startDate.stored,
            "discontinue" to discontinued,
            "notes" to notes.text.toString().trim(),
            "childId" to childId
        )
        // Only set when discontinuing, so an end date planned on an older record isn't erased
        if (discontinued) medicationMap["endDate"] = endDate.stored
        // Merge keeps fields this screen doesn't show (e.g. frequency from older versions)
        val medications = Firebase.firestore.collection("Medications")
        val ref = medicationId?.let { medications.document(it) } ?: medications.document()
        saveButton.isEnabled = false
        ref.set(medicationMap, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, if (medicationId == null) "Medication added" else "Medication updated", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                saveButton.isEnabled = true
                Toast.makeText(this, "Failed to save medication: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
