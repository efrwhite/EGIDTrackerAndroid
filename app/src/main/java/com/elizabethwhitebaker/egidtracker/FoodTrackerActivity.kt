package com.elizabethwhitebaker.egidtracker

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FoodTrackerActivity : AppCompatActivity() {

    private lateinit var addButton: Button
    private lateinit var dateButton: Button
    private lateinit var foodTableLayout: TableLayout

    private var selectedDate: String = ""
    private var childId: String? = null

    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_food_tracker)

        // Initialize UI components
        foodTableLayout = findViewById(R.id.pastAllergenTableLayout)
        addButton = findViewById(R.id.addButton)
        dateButton = findViewById(R.id.date)

        // Get childId from SharedPreferences
        childId = getCurrentChildId()

        if (savedInstanceState == null) {
            selectedDate = getCurrentDate() // Set initial date on first launch
        }

        dateButton.text = selectedDate

        // Load food entries for the selected date
        loadFoodEntries()

        dateButton.setOnClickListener { openDatePicker() }

        // Navigate to AddFoodActivity with the selected date
        addButton.setOnClickListener {
            val intent = Intent(this, AddFoodActivity::class.java).apply {
                putExtra("selectedDate", selectedDate)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadFoodEntries() // Reload entries when returning to this activity
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
                loadFoodEntries() // Reload entries for the new date
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

    private fun loadFoodEntries() {
        foodTableLayout.removeAllViews()

        if (childId == null) {
            Toast.makeText(this, "Child ID not found.", Toast.LENGTH_SHORT).show()
            return
        }

        // Get all entries for the child
        db.collection("FoodEntries")
            .document(childId!!)
            .collection("Entries")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val filteredFoods = querySnapshot.documents.mapNotNull { document ->
                    val date = document.getString("date")
                    val foodName = document.getString("foodName")
                    val notes = document.getString("notes")
                    if (date == selectedDate) {
                        Triple(document.id, foodName ?: "Unknown Food", notes ?: "")
                    } else {
                        null
                    }
                }
                populateFoodTable(filteredFoods)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load foods: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateFoodTable(foods: List<Triple<String, String, String>>) {
        foodTableLayout.removeAllViews() // Clear previous entries

        foods.forEach { (entryId, foodName, notes) ->
            val row = layoutInflater.inflate(R.layout.table_row_item, null) as TableRow
            val foodNameTextView = row.findViewById<TextView>(R.id.nameTextView)
            val editButton = row.findViewById<Button>(R.id.editButton)

            foodNameTextView.text = foodName

            // Set up the edit button
            editButton.text = "Edit"
            editButton.setOnClickListener {
                navigateToEditActivity(entryId, foodName, notes)  // Pass notes to the edit screen
            }

            foodTableLayout.addView(row)
        }
    }


    private fun navigateToEditActivity(entryId: String, foodName: String, notes: String) {
        val intent = Intent(this, AddFoodActivity::class.java).apply {
            putExtra("entryId", entryId)
            putExtra("foodName", foodName)
            putExtra("notes", notes)  // Pass the notes along with the intent
            putExtra("selectedDate", selectedDate)
        }
        startActivity(intent)
    }

}
