package com.elizabethwhitebaker.egidtracker

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Date

/** Adds a food entry, or edits one (extras: entryId, foodName, notes, selectedDate). */
class AddFoodActivity : AppCompatActivity() {

    private lateinit var dateField: DateField
    private lateinit var foodNameLayout: TextInputLayout
    private lateinit var foodNameField: EditText
    private lateinit var notesField: EditText
    private lateinit var saveButton: Button

    private val childId by lazy { CurrentChild.getId(this) }
    private val entryId by lazy { intent.getStringExtra("entryId") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_food)
        setupPageHeader(if (entryId == null) R.string.add_food_short else R.string.edit_food)
        if (requireSelectedChild() == null) return // needs a child; offers to add one

        foodNameLayout = findViewById(R.id.foodNameLayout)
        foodNameField = findViewById(R.id.foodNameField)
        notesField = findViewById(R.id.notesField)
        saveButton = findViewById(R.id.saveButton)

        dateField = DateField(this, findViewById(R.id.dateLayout), FoodTrackerActivity.FOOD_DATE_PATTERN)
        val selectedDate = intent.getStringExtra("selectedDate")
        if (selectedDate.isNullOrBlank()) dateField.date = Date() else dateField.stored = selectedDate

        if (entryId != null) {
            foodNameField.setText(intent.getStringExtra("foodName"))
            notesField.setText(intent.getStringExtra("notes"))
            findViewById<View>(R.id.deleteButton).apply {
                visibility = View.VISIBLE
                setOnClickListener { confirmDelete() }
            }
        }

        foodNameField.doAfterTextChanged { foodNameLayout.error = null }
        saveButton.setOnClickListener { saveFoodEntry() }
    }

    private fun entryRef(): DocumentReference? {
        val child = childId ?: return null
        val entries = Firebase.firestore.collection("FoodEntries").document(child).collection("Entries")
        return entryId?.let { entries.document(it) } ?: entries.document()
    }

    private fun saveFoodEntry() {
        val foodName = foodNameField.text.toString().trim()
        if (foodName.isEmpty()) {
            foodNameLayout.error = getString(R.string.error_required)
            return
        }
        val ref = entryRef() ?: run {
            Toast.makeText(this, "No child selected", Toast.LENGTH_SHORT).show()
            return
        }
        val foodData = hashMapOf(
            "date" to dateField.stored,
            "foodName" to foodName,
            "notes" to notesField.text.toString().trim()
        )
        saveButton.isEnabled = false
        awaitSave(ref.set(foodData, SetOptions.merge()),
            onSaved = { queued ->
                toastSaved(if (entryId == null) "Food added" else "Food updated", queued)
                finish()
            },
            onFailed = { e ->
                saveButton.isEnabled = true
                Toast.makeText(this, "Failed to save food: ${e.message}", Toast.LENGTH_SHORT).show()
            })
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_food_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val ref = entryRef() ?: return@setPositiveButton
                awaitSave(ref.delete(),
                    onSaved = { queued ->
                        toastSaved("Food entry deleted", queued)
                        finish()
                    },
                    onFailed = { e ->
                        Toast.makeText(this, "Failed to delete food: ${e.message}", Toast.LENGTH_SHORT).show()
                    })
            }
            .show()
    }
}
