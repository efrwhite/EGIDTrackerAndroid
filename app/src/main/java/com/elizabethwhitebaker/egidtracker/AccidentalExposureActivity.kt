package com.elizabethwhitebaker.egidtracker

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AccidentalExposureActivity : AppCompatActivity() {
    private lateinit var datePickerButton: Button
    private lateinit var calender: Calendar
    private lateinit var saveButton: Button

    private lateinit var itemTableLayout: TableLayout

    private lateinit var itemName: EditText
    private lateinit var date: String
    private var childId: String? = null
    private var itemId: String? = null

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance()}


     override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accidental_exposure)

        datePickerButton = findViewById(R.id.datePickerButton)
        saveButton = findViewById(R.id.addAEButton)

        itemName = findViewById(R.id.exposedItemTextField)
        date = datePickerButton.text.toString()
        childId = getCurrentChildId()

        calender = Calendar.getInstance()

        itemTableLayout  = findViewById(R.id.AEItemTableLayout)

        datePickerButton.setOnClickListener {
            showDatePickerDialog(datePickerButton, calender)
        }

         saveButton.setOnClickListener {
             saveItem()
         }

        fetchAndDisplayItems(itemTableLayout)

     }

    private fun getCurrentChildId(): String? {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("CurrentChildId", null)
    }


    private fun saveItem() {

        val itemMap = hashMapOf(
            "itemName" to itemName.text.toString().trim(),
            "date" to datePickerButton.text.toString().trim(),
            "childId" to childId
        )

        firestore.collection("AccidentalExposure").add(itemMap)
            .addOnSuccessListener {documentReference ->
                val medicationId = documentReference.id
                Toast.makeText(this, "Medication added successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener{e ->
                Toast.makeText(this, "Failed to add medication: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        val intent = Intent(this, AccidentalExposureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()

    }


    private fun fetchAndDisplayItems(itemTableLayout: TableLayout) {

        val childId = getCurrentChildId()
        Firebase.firestore.collection("AccidentalExposure")
            .whereEqualTo("childId", childId)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val itemName = document.getString("itemName") ?: "No Name"
                    val date = document.getString("date") ?: "None"
                    addRowToTable(itemTableLayout, itemName, date)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to load past items: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }

    }


    @SuppressLint("InflateParams")
    private fun addRowToTable(itemTableLayout: TableLayout, itemName: String, date: String) {

        val row = layoutInflater.inflate(R.layout.accidental_exposure_table_item, null)
        val nameTextView = row.findViewById<TextView>(R.id.nameTextView)
        val dateTextView = row.findViewById<TextView>(R.id.dateTextView)

        nameTextView.text = "$itemName"
        dateTextView.text = "$date"

        itemTableLayout.addView(row)

    }


    private fun showDatePickerDialog(button: Button, calendar: Calendar) {
        val datePicker = DatePickerDialog(
            this,
            { view, year, monthOfYear, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, monthOfYear, dayOfMonth)
                val formattedDate = formatDate(selectedDate.time)
                button.text = formattedDate
                date = formattedDate
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