package com.elizabethwhitebaker.egidtracker

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddFoodActivity : AppCompatActivity() {

    private lateinit var dateButton: Button
    private lateinit var foodNameField: EditText
    private lateinit var notesField: EditText
    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button

    private val db = Firebase.firestore
    private var selectedDate: String = ""
    private var originalDate: String = ""
    private var foodName: String? = null
    private var notes: String? = null
    private var isEditing: Boolean = false
    private var childId: String? = null
    private var entryId: String? = null  // Holds the ID of the entry being edited

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_food)

        // Initialize UI components
        dateButton = findViewById(R.id.date)
        foodNameField = findViewById(R.id.foodNameField)
        notesField = findViewById(R.id.notesField)
        saveButton = findViewById(R.id.saveButton)
        deleteButton = findViewById(R.id.deleteButton)

        childId = getCurrentChildId()

        // Retrieve data from the intent
        originalDate = intent.getStringExtra("selectedDate") ?: getCurrentDate()
        selectedDate = originalDate
        foodName = intent.getStringExtra("foodName")
        notes = intent.getStringExtra("notes")  // Retrieve notes from the intent
        entryId = intent.getStringExtra("entryId")

        // Set date button text
        dateButton.text = selectedDate

        // Check if in edit mode
        isEditing = foodName != null
        if (isEditing) {
            populateFieldsForEditing()
            deleteButton.visibility = Button.VISIBLE
        }

        dateButton.setOnClickListener { openDatePicker() }
        saveButton.setOnClickListener { if (isEditing) updateFoodEntry() else saveNewFoodEntry() }
        deleteButton.setOnClickListener { deleteFoodEntry() }
    }

    private fun populateFieldsForEditing() {
        foodNameField.setText(foodName)
        notesField.setText(notes)  // Populate the notes field
    }


    private fun getCurrentChildId(): String? {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("CurrentChildId", null)
    }

    private fun openDatePicker() {
        val calendar = Calendar.getInstance()
        val dateParts = selectedDate.split("-")
        calendar.set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt())

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate = formatDate(year, month, day)
                dateButton.text = selectedDate
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun formatDate(year: Int, month: Int, day: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }

    private fun saveNewFoodEntry() {
        if (childId == null) {
            Toast.makeText(this, "Child ID not found.", Toast.LENGTH_SHORT).show()
            return
        }

        val foodData = hashMapOf(
            "date" to selectedDate,
            "foodName" to foodNameField.text.toString().trim(),
            "notes" to notesField.text.toString().trim()
        )

        val childDocRef = db.collection("FoodEntries")
            .document(childId!!)
            .collection("Entries")
            .document()

        childDocRef.set(foodData, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Food entry added successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to add food: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateFoodEntry() {
        if (childId == null || entryId == null) {
            Toast.makeText(this, "Child ID or Entry ID not found.", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedFoodData = hashMapOf(
            "date" to selectedDate,
            "foodName" to foodNameField.text.toString().trim(),
            "notes" to notesField.text.toString().trim()
        )

        val entryDocRef = db.collection("FoodEntries")
            .document(childId!!)
            .collection("Entries")
            .document(entryId!!)

        entryDocRef.set(updatedFoodData, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Food entry updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update food: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteFoodEntry() {
        if (childId == null || entryId == null) {
            Toast.makeText(this, "Child ID or Entry ID not found.", Toast.LENGTH_SHORT).show()
            return
        }

        val entryDocRef = db.collection("FoodEntries")
            .document(childId!!)
            .collection("Entries")
            .document(entryId!!)

        entryDocRef.delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Food entry deleted successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete food: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
