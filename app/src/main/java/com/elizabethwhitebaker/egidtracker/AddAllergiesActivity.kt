package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.firestore.FirebaseFirestore

class AddAllergiesActivity : AppCompatActivity() {
    
    private lateinit var saveButton: Button
    private lateinit var allergenName: EditText
    private lateinit var frequency: EditText
    private lateinit var discontinue: SwitchCompat
    private lateinit var notes: EditText
    private var childId: String? = null
    private var allergenId: String? = null

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance()}


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_allergies)

        allergenName = findViewById(R.id.allergiesNameField)
        saveButton = findViewById(R.id.saveButton)
        frequency = findViewById(R.id.frequencyField)
        discontinue = findViewById(R.id.discontinueSwitch)
        notes = findViewById(R.id.allergyNotesField)
        childId = getCurrentChildId()

        // Check if in edit mode
        allergenId = intent.getStringExtra("allergenId")
        if (allergenId != null) {
            fetchAndPopulateAllergenData(allergenId!!)
        }

        saveButton.setOnClickListener {
            if(allergenId == null) {
                saveAllergen()
            } else {
                updateAllergen()
            }
        }
    }

    private fun getCurrentChildId(): String? {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return sharedPreferences.getString("CurrentChildId", null)
    }

    private fun saveAllergen() {
        val allergenMap = hashMapOf(
            "allergenName" to allergenName.text.toString().trim(),
            "frequency" to frequency.text.toString().trim(),
            "discontinue" to discontinue.isChecked,
            "notes" to notes.text.toString().trim(),
            "childId" to childId
        )

        firestore.collection("Allergens").add(allergenMap)
            .addOnSuccessListener { documentReference ->
                val allergenId = documentReference.id
                Toast.makeText(this, "Allergen added successfully", Toast.LENGTH_SHORT).show()
                // Start AllergiesActivity and finish this activity
                val intent = Intent(this, AllergiesActivity::class.java)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to add allergen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateAllergen() {
        val allergenMap = hashMapOf(
            "allergenName" to allergenName.text.toString().trim(),
            "frequency" to frequency.text.toString().trim(),
            "discontinue" to discontinue.isChecked,
            "notes" to notes.text.toString().trim(),
            "childId" to childId
        )

        allergenId?.let {
            firestore.collection("Allergens").document(it).set(allergenMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Allergen updated successfully", Toast.LENGTH_SHORT).show()
                    // Start AllergiesActivity and finish this activity
                    val intent = Intent(this, AllergiesActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update allergen: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }


    private fun fetchAndPopulateAllergenData(allergenId: String) {
        firestore.collection("Allergens").document(allergenId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    allergenName.setText(document.getString("allergenName"))
                    frequency.setText(document.getString("frequency"))
                    discontinue.isChecked = document.getBoolean("discontinue") ?: false
                    notes.setText(document.getString("notes"))

                } else {
                    Toast.makeText(this, "Allergen not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load allergen data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}