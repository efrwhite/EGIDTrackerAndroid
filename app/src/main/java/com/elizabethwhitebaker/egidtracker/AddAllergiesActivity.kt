package com.elizabethwhitebaker.egidtracker

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddAllergiesActivity : AppCompatActivity() {

    private lateinit var clearedDatePickerButton: Button
    private lateinit var diagnosisDatePickerButton: Button
    private lateinit var calendar: Calendar

    private lateinit var title: TextView
    private lateinit var saveButton: Button
    private lateinit var allergenName: EditText
    private lateinit var diagnosisDate: String
    private lateinit var igE: Switch
    private lateinit var cleared: Switch
    private lateinit var clearedDate: String
    private lateinit var notes: EditText
    private var childId: String? = null
    private var allergenId: String? = null

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private var isViewOnly = false // flag for view-only mode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_allergies)

        title = findViewById(R.id.title)
        clearedDatePickerButton = findViewById(R.id.clearedDatePickerButton)
        diagnosisDatePickerButton = findViewById(R.id.diagnosisDate)
        allergenName = findViewById(R.id.allergiesNameField)
        saveButton = findViewById(R.id.saveButton)
        cleared = findViewById(R.id.clearSwitch)
        igE = findViewById(R.id.igESwitch)
        notes = findViewById(R.id.allergyNotesField)

        calendar = Calendar.getInstance()

        // Get the view mode flag from intent
        isViewOnly = intent.getBooleanExtra("isViewOnly", false)

        if (isViewOnly) {
            enableViewMode() // If in view mode, disable all fields and change title
        } else {
            // Set up save functionality only if not in view-only mode
            saveButton.setOnClickListener {
                if (allergenId == null) {
                    saveAllergen()
                } else {
                    updateAllergen()
                }
            }
        }

        // Make notes field always editable
        notes.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isViewOnly) {
                    // Change "Back" button to "Save" button if notes are modified
                    saveButton.text = "Save"
                    saveButton.setOnClickListener {
                        // Only save notes if we are in view-only mode
                        if (allergenId != null) {
                            updateAllergenNotes()
                        }
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        clearedDatePickerButton.setOnClickListener {
            if (!isViewOnly) {
                showDatePickerDialog(clearedDatePickerButton)
            }
        }

        diagnosisDatePickerButton.setOnClickListener {
            if (!isViewOnly) {
                showDatePickerDialog(diagnosisDatePickerButton)
            }
        }

        childId = getCurrentChildId()

        // Check if in edit mode
        allergenId = intent.getStringExtra("allergenId")
        if (allergenId != null) {
            fetchAndPopulateAllergenData(allergenId!!)
        }
    }

    private fun getCurrentChildId(): String? {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return sharedPreferences.getString("CurrentChildId", null)
    }

    private fun saveAllergen() {
        if (!validateClearedFields()) return

        val allergenMap = hashMapOf(
            "allergenName" to allergenName.text.toString().trim(),
            "diagnosisDate" to diagnosisDatePickerButton.text.toString().trim(),
            "igE" to igE.isChecked,
            "cleared" to cleared.isChecked,
            "clearedDate" to clearedDatePickerButton.text.toString().trim(),
            "notes" to notes.text.toString().trim(),
            "childId" to childId
        )

        firestore.collection("Allergens").add(allergenMap)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(this, "Allergen added successfully", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, AllergiesActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to add allergen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateAllergen() {
        if (!validateClearedFields()) return

        val allergenMap = hashMapOf(
            "allergenName" to allergenName.text.toString().trim(),
            "diagnosisDate" to diagnosisDatePickerButton.text.toString().trim(),
            "igE" to igE.isChecked,
            "cleared" to cleared.isChecked,
            "clearedDate" to clearedDatePickerButton.text.toString().trim(),
            "notes" to notes.text.toString().trim(),
            "childId" to childId
        )

        allergenId?.let {
            firestore.collection("Allergens").document(it).set(allergenMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Allergen updated successfully", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, AllergiesActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update allergen: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun fetchAndPopulateAllergenData(allergenId: String) {
        title.text = if (isViewOnly) "View Past Allergen" else "Edit Allergen"
        firestore.collection("Allergens").document(allergenId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    allergenName.setText(document.getString("allergenName"))
                    diagnosisDate = document.getString("diagnosisDate") ?: ""
                    igE.isChecked = document.getBoolean("igE") ?: false
                    cleared.isChecked = document.getBoolean("cleared") ?: false
                    clearedDate = document.getString("clearedDate") ?: ""
                    notes.setText(document.getString("notes"))

                    diagnosisDatePickerButton.text = diagnosisDate
                    clearedDatePickerButton.text = clearedDate

                    if (isViewOnly) {
                        disableInteraction() // Disable all interactions except notes
                    }
                } else {
                    Toast.makeText(this, "Allergen not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load allergen data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDatePickerDialog(button: Button) {
        val datePicker = DatePickerDialog(
            this,
            { _, year, monthOfYear, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, monthOfYear, dayOfMonth)
                val formattedDate = formatDate(selectedDate.time)
                button.text = formattedDate
                if (button == diagnosisDatePickerButton) {
                    diagnosisDate = formattedDate
                } else {
                    clearedDate = formattedDate
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat("MM-dd-yyyy", Locale.getDefault())
        return sdf.format(date)
    }

    private fun validateClearedFields(): Boolean {
        if (cleared.isChecked && clearedDatePickerButton.text.toString() == "Enter Date") {
            Toast.makeText(this, "Please enter a cleared date when clearing an allergen", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun updateAllergenNotes() {
        val notesMap = hashMapOf(
            "notes" to notes.text.toString().trim()
        )

        allergenId?.let {
            firestore.collection("Allergens").document(it).update(notesMap as Map<String, Any>)
                .addOnSuccessListener {
                    Toast.makeText(this, "Notes updated successfully", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, AllergiesActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update notes: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun enableViewMode() {
        title.text = "View Cleared Allergen"

        saveButton.text = "Back"
        saveButton.isEnabled = true
        saveButton.setOnClickListener {
            val intent = Intent(this, AllergiesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }

        disableInteraction()
    }

    private fun disableInteraction() {
        allergenName.isEnabled = false
        diagnosisDatePickerButton.isEnabled = false
        clearedDatePickerButton.isEnabled = false
        igE.isEnabled = false
        cleared.isEnabled = false
        // Notes remain editable
    }
}
