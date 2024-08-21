package com.elizabethwhitebaker.egidtracker

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddMedicationActivity : AppCompatActivity() {

    private lateinit var datePickerButton: Button
    private lateinit var endDatePickerButton: Button
    private lateinit var calendar: Calendar
    private lateinit var saveButton: Button

    private lateinit var medName: EditText
    private lateinit var dosage: EditText
    private lateinit var startDate: String
    private lateinit var endDate: String
    private lateinit var frequency: EditText
    private lateinit var discontinue: Switch
    private lateinit var notes: EditText
    private var childId: String? = null
    private var medicationId: String? = null

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance()}


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_addmedication)

        datePickerButton = findViewById(R.id.datePickerButton)
        endDatePickerButton = findViewById(R.id.endDatePickerButton)
        saveButton = findViewById(R.id.medSaveButton)

        medName = findViewById(R.id.medNameField)
        dosage = findViewById(R.id.medAmountField)
        frequency = findViewById(R.id.frequencyField)
        startDate = datePickerButton.text.toString()
        endDate = endDatePickerButton.text.toString()
        discontinue = findViewById(R.id.discontinueSwitch)
        notes = findViewById(R.id.medNotesField)
        childId = getCurrentChildId()

        calendar = Calendar.getInstance()

        datePickerButton.setOnClickListener {
            showDatePickerDialog(datePickerButton)
        }

        endDatePickerButton.setOnClickListener {
            showDatePickerDialog(endDatePickerButton)
        }

        // Check if in edit mode
        medicationId = intent.getStringExtra("medicationId")
        if (medicationId != null) {
            fetchAndPopulateMedicationData(medicationId!!)
        }

        saveButton.setOnClickListener {
            if(medicationId == null) {
                saveMedication()
            } else {
                updateMedication()
            }
        }

    }

    private fun getCurrentChildId(): String? {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("CurrentChildId", null)
    }

    private fun saveMedication() {
        val discontinueChecked = discontinue.isChecked
        val endDateText = endDatePickerButton.text.toString().trim()

        // Validate end date if discontinue is true
        if (discontinueChecked && endDateText.equals("Enter Date")) {
            Toast.makeText(this, "End date is required when discontinuing medication", Toast.LENGTH_SHORT).show()
            return // Exit the method if validation fails
        }

        val medicationMap = hashMapOf(
            "medName" to medName.text.toString().trim(),
            "dosage" to dosage.text.toString().trim(),
            "startDate" to datePickerButton.text.toString().trim(),
            "endDate" to endDatePickerButton.text.toString().trim(),
            "frequency" to frequency.text.toString().trim(),
            "discontinue" to discontinue.isChecked,
            "notes" to notes.text.toString().trim(),
            "childId" to  childId
        )

        firestore.collection("Medications").add(medicationMap)
            .addOnSuccessListener {documentReference ->
                val medicationId = documentReference.id
                Toast.makeText(this, "Medication added successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener{e ->
                Toast.makeText(this, "Failed to add medication: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        val intent = Intent(this, MedicationsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun updateMedication() {
        val discontinueChecked = discontinue.isChecked
        val endDateText = endDatePickerButton.text.toString().trim()

        // Validate end date if discontinue is true
        if (discontinueChecked && endDateText.equals("Enter Date")) {
            Toast.makeText(this, "End date is required when discontinuing medication", Toast.LENGTH_SHORT).show()
            return // Exit the method if validation fails
        }

        val medicationMap = hashMapOf(
            "medName" to medName.text.toString().trim(),
            "dosage" to dosage.text.toString().trim(),
            "startDate" to startDate.trim(),
            "endDate" to endDate.trim(),
            "frequency" to frequency.text.toString().trim(),
            "discontinue" to discontinue.isChecked,
            "notes" to notes.text.toString().trim(),
            "childId" to  childId
        )

        medicationId?.let {
            firestore.collection("Medications").document(it).set(medicationMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Medication updated successfully", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {e ->
                    Toast.makeText(this, "Failed to update medication: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        val intent = Intent(this, MedicationsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun fetchAndPopulateMedicationData(medicationId: String) {
        firestore.collection("Medications").document(medicationId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    medName.setText(document.getString("medName"))
                    dosage.setText(document.getString("dosage"))
                    startDate = document.getString("startDate") ?: ""
                    endDate = document.getString("endDate") ?: ""
                    frequency.setText(document.getString("frequency"))
                    discontinue.isChecked = document.getBoolean("discontinue") ?: false
                    notes.setText(document.getString("notes"))

                    datePickerButton.text = startDate
                    endDatePickerButton.text = endDate

                } else {
                    Toast.makeText(this, "Medication not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load medication data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDatePickerDialog(button: Button) {
        val datePicker = DatePickerDialog(
            this,
            { view, year, monthOfYear, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, monthOfYear, dayOfMonth)
                val formattedDate = formatDate(selectedDate.time)
                button.text = formattedDate
                if (button == datePickerButton) {
                    startDate = formattedDate // Save startDate
                } else {
                    endDate = formattedDate // Save endDate
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


}