package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Date

/** The selected child's food log for one day; tap an entry to edit it. */
class FoodTrackerActivity : AppCompatActivity() {

    companion object {
        /** Food entries store their day in this format (also read by HomeActivity's stats). */
        const val FOOD_DATE_PATTERN = "yyyy-MM-dd"
    }

    private lateinit var dateField: DateField
    private lateinit var listLabel: TextView
    private lateinit var rows: LinearLayout
    private lateinit var emptyView: View

    private val childId by lazy { CurrentChild.getId(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_food_tracker)
        setupPageHeader(R.string.foodTracker)

        listLabel = findViewById(R.id.foodListLabel)
        rows = findViewById(R.id.foodRows)
        emptyView = findViewById(R.id.foodEmpty)

        dateField = DateField(this, findViewById<TextInputLayout>(R.id.dateLayout), FOOD_DATE_PATTERN)
        dateField.date = Date()
        dateField.onPicked = { loadFoodEntries() }

        findViewById<View>(R.id.addButton).setOnClickListener {
            startActivity(Intent(this, AddFoodActivity::class.java).apply {
                putExtra("selectedDate", dateField.stored)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        loadFoodEntries() // also picks up changes made in AddFoodActivity
    }

    private fun loadFoodEntries() {
        val date = dateField.date ?: return
        listLabel.text = if (DateUtils.isToday(date.time)) getString(R.string.todays_food)
            else getString(R.string.food_on_date, findViewById<TextInputLayout>(R.id.dateLayout).editText?.text)

        val id = childId ?: run {
            Toast.makeText(this, "No child selected", Toast.LENGTH_SHORT).show()
            return
        }
        val day = dateField.stored
        Firebase.firestore.collection("FoodEntries").document(id).collection("Entries")
            .whereEqualTo("date", day)
            .get()
            .addOnSuccessListener { snapshot ->
                if (dateField.stored != day) return@addOnSuccessListener // the day changed meanwhile
                val entries = snapshot.documents.map { doc ->
                    val foodName = doc.getString("foodName").orEmpty().ifBlank { "Unknown food" }
                    val notes = doc.getString("notes").orEmpty()
                    ListRow(foodName, notes.takeIf { it.isNotBlank() }, getString(R.string.edit)) {
                        startActivity(Intent(this, AddFoodActivity::class.java).apply {
                            putExtra("entryId", doc.id)
                            putExtra("foodName", foodName)
                            putExtra("notes", notes)
                            putExtra("selectedDate", day)
                        })
                    }
                }
                fillList(rows, emptyView, entries)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load foods: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
