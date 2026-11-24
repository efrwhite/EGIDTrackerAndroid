package com.elizabethwhitebaker.egidtracker

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.gms.tasks.Task
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Adds an allergen, or edits one (extra: allergenId). Cleared allergens open view-only
 * (isViewOnly): everything is locked except the notes.
 */
class AddAllergiesActivity : AppCompatActivity() {

    companion object {
        /** Allergens store their dates in this format. */
        private const val ALLERGEN_DATE_PATTERN = "MM-dd-yyyy"
    }

    private lateinit var nameLayout: TextInputLayout
    private lateinit var allergenName: EditText
    private lateinit var igE: SwitchMaterial
    private lateinit var cleared: SwitchMaterial
    private lateinit var severityLayout: TextInputLayout
    private lateinit var severity: AutoCompleteTextView
    private lateinit var notes: EditText
    private lateinit var diagnosisDate: DateField
    private lateinit var clearedDate: DateField
    private lateinit var saveButton: Button

    private val childId by lazy { CurrentChild.getId(this) }
    private val allergenId by lazy { intent.getStringExtra("allergenId") }
    private val isViewOnly by lazy { intent.getBooleanExtra("isViewOnly", false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_allergies)
        setupPageHeader(when {
            isViewOnly -> R.string.view_allergen
            allergenId != null -> R.string.edit_allergen
            else -> R.string.add_allergen_short
        })

        nameLayout = findViewById(R.id.allergenNameLayout)
        allergenName = findViewById(R.id.allergiesNameField)
        igE = findViewById(R.id.igESwitch)
        cleared = findViewById(R.id.clearSwitch)
        severityLayout = findViewById(R.id.severityLayout)
        severity = findViewById(R.id.severityInput)
        notes = findViewById(R.id.allergyNotesField)
        saveButton = findViewById(R.id.saveButton)
        diagnosisDate = DateField(this, findViewById(R.id.diagnosisDateLayout), ALLERGEN_DATE_PATTERN)
        clearedDate = DateField(this, findViewById(R.id.clearedDateLayout), ALLERGEN_DATE_PATTERN)

        severity.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1,
            resources.getStringArray(R.array.severity_options)))
        severity.setOnItemClickListener { _, _, _, _ -> severityLayout.error = null }
        allergenName.doAfterTextChanged { nameLayout.error = null }
        cleared.setOnCheckedChangeListener { _, checked ->
            findViewById<View>(R.id.clearedDateGroup).visibility = if (checked) View.VISIBLE else View.GONE
        }

        if (isViewOnly) {
            lockAllButNotes()
            saveButton.setText(R.string.save_notes)
        }
        allergenId?.let { loadAllergen(it) }
        saveButton.setOnClickListener { if (isViewOnly) saveNotes() else saveAllergen() }
    }

    private fun loadAllergen(id: String) {
        Firebase.firestore.collection("Allergens").document(id).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Allergen not found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                allergenName.setText(doc.getString("allergenName"))
                igE.isChecked = doc.getBoolean("igE") ?: false
                cleared.isChecked = doc.getBoolean("cleared") ?: false
                diagnosisDate.stored = doc.getString("diagnosisDate").orEmpty()
                clearedDate.stored = doc.getString("clearedDate").orEmpty()
                notes.setText(doc.getString("notes"))
                // Older records saved "Select Severity" when none was chosen
                doc.getString("severity")
                    ?.takeIf { it in resources.getStringArray(R.array.severity_options) }
                    ?.let { severity.setText(it, false) }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load allergen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun lockAllButNotes() {
        nameLayout.isEnabled = false
        igE.isEnabled = false
        cleared.isEnabled = false
        severityLayout.isEnabled = false
        diagnosisDate.isEnabled = false
        clearedDate.isEnabled = false
    }

    private fun saveAllergen() {
        val name = allergenName.text.toString().trim()
        var valid = true
        if (name.isEmpty()) { nameLayout.error = getString(R.string.error_required); valid = false }
        if (severity.text.isNullOrBlank()) { severityLayout.error = getString(R.string.error_required); valid = false }
        if (cleared.isChecked && !clearedDate.requireDate()) valid = false
        if (!valid) return

        val allergenMap = hashMapOf(
            "allergenName" to name,
            "diagnosisDate" to diagnosisDate.stored,
            "severity" to severity.text.toString(),
            "igE" to igE.isChecked,
            "cleared" to cleared.isChecked,
            "clearedDate" to if (cleared.isChecked) clearedDate.stored else "",
            "notes" to notes.text.toString().trim(),
            "childId" to childId
        )
        val allergens = Firebase.firestore.collection("Allergens")
        val ref = allergenId?.let { allergens.document(it) } ?: allergens.document()
        save { ref.set(allergenMap, SetOptions.merge()) }
    }

    private fun saveNotes() {
        val id = allergenId ?: return
        save {
            Firebase.firestore.collection("Allergens").document(id)
                .update("notes", notes.text.toString().trim())
        }
    }

    private fun save(write: () -> Task<Void>) {
        saveButton.isEnabled = false
        write()
            .addOnSuccessListener {
                Toast.makeText(this, "Allergen saved", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                saveButton.isEnabled = true
                Toast.makeText(this, "Failed to save allergen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
