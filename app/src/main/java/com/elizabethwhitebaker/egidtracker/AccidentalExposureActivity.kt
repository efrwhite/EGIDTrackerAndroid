package com.elizabethwhitebaker.egidtracker

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Locale

/** Report an accidental exposure for the selected child, and see past ones (newest first). */
class AccidentalExposureActivity : AppCompatActivity() {

    companion object {
        /** Exposures store their date in this format. */
        private const val EXPOSURE_DATE_PATTERN = "MM-dd-yyyy"
    }

    private lateinit var dateField: DateField
    private lateinit var itemLayout: TextInputLayout
    private lateinit var itemName: EditText
    private lateinit var notesField: EditText
    private lateinit var addButton: Button

    private val childId by lazy { CurrentChild.getId(this) }
    private val storedFormat = SimpleDateFormat(EXPOSURE_DATE_PATTERN, Locale.US)
    private val displayFormat = SimpleDateFormat(DateField.DISPLAY_PATTERN, Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accidental_exposure)
        setupPageHeader(R.string.accidental_exposure_title)

        dateField = DateField(this, findViewById(R.id.dateLayout), EXPOSURE_DATE_PATTERN)
        itemLayout = findViewById(R.id.itemLayout)
        itemName = findViewById(R.id.exposedItemTextField)
        notesField = findViewById(R.id.descriptionTextField)
        addButton = findViewById(R.id.addAEButton)

        itemName.doAfterTextChanged { itemLayout.error = null }
        addButton.setOnClickListener { saveExposure() }
        loadHistory()
    }

    private fun saveExposure() {
        val item = itemName.text.toString().trim()
        var valid = dateField.requireDate()
        if (item.isEmpty()) { itemLayout.error = getString(R.string.error_required); valid = false }
        if (!valid) return

        // Field names kept from earlier versions: itemName = what it was, description = notes
        val exposure = hashMapOf(
            "itemName" to item,
            "description" to notesField.text.toString().trim(),
            "date" to dateField.stored,
            "childId" to childId
        )
        addButton.isEnabled = false
        Firebase.firestore.collection("AccidentalExposure").add(exposure)
            .addOnSuccessListener {
                addButton.isEnabled = true
                Toast.makeText(this, "Exposure added", Toast.LENGTH_SHORT).show()
                dateField.date = null
                itemName.text = null
                notesField.text = null
                itemLayout.error = null
                loadHistory()
            }
            .addOnFailureListener { e ->
                addButton.isEnabled = true
                Toast.makeText(this, "Failed to add exposure: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadHistory() {
        val id = childId ?: return
        Firebase.firestore.collection("AccidentalExposure")
            .whereEqualTo("childId", id)
            .get()
            .addOnSuccessListener { documents ->
                val rows = documents
                    .map { doc -> doc to runCatching { storedFormat.parse(doc.getString("date").orEmpty()) }.getOrNull() }
                    .sortedByDescending { (_, date) -> date?.time ?: 0L }
                    .map { (doc, date) ->
                        val subtitle = listOfNotNull(
                            date?.let(displayFormat::format),
                            doc.getString("description")?.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        ListRow(doc.getString("itemName").orEmpty().ifBlank { "No name" }, subtitle)
                    }
                fillList(findViewById<LinearLayout>(R.id.historyRows), findViewById(R.id.historyEmpty), rows)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load exposures: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
