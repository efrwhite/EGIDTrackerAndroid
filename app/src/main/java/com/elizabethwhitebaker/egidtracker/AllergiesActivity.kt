package com.elizabethwhitebaker.egidtracker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
class AllergiesActivity : AppCompatActivity() {

    private lateinit var addAllergenButton: Button
    private lateinit var childId: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_allergies)

        val allergenTableLayout: TableLayout = findViewById(R.id.allergenTableLayout)
        val pastAllergenTableLayout: TableLayout = findViewById(R.id.pastAllergenTableLayout)

        //Initialize button
        addAllergenButton = findViewById(R.id.addAllergenButton)

        //Set onlick
        addAllergenButton.setOnClickListener {
            intent = Intent(this, AddAllergiesActivity::class.java)
            startActivity(intent)
        }

        fetchAndDisplayAllergies(allergenTableLayout, pastAllergenTableLayout)

    }

    private fun getCurrentChildId(): String? {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("CurrentChildId", null)
    }

    private fun fetchAndDisplayAllergies(allergyTableLayout: TableLayout, pastAllergyTableLayout: TableLayout) {
        val childId = getCurrentChildId()

        Firebase.firestore.collection("Allergens")
            .whereEqualTo("childId", childId)
            .get()
            .addOnSuccessListener { documents ->
                val igEAllergies = mutableListOf<Map<String, Any>>()
                val nonIgEAllergies = mutableListOf<Map<String, Any>>()

                // Separate allergies into IgE and Non-IgE groups
                for (document in documents) {
                    val allergenName = document.getString("allergenName") ?: "No Name"
                    val allergenId = document.id
                    val igE = document.getBoolean("igE") ?: false
                    val isCleared = document.getBoolean("cleared") ?: false

                    val allergyData = mapOf(
                        "allergenName" to allergenName,
                        "allergenId" to allergenId,
                        "isCleared" to isCleared,
                        "igE" to igE
                    )

                    if (igE) {
                        igEAllergies.add(allergyData)
                    } else {
                        nonIgEAllergies.add(allergyData)
                    }
                }

                // Sort each group alphabetically by allergenName
                igEAllergies.sortBy { it["allergenName"].toString() }
                nonIgEAllergies.sortBy { it["allergenName"].toString() }

                // Add rows for IgE allergies
                for (allergy in igEAllergies) {
                    addRowToTable(
                        allergyTableLayout,
                        pastAllergyTableLayout,
                        allergy["allergenName"] as String,
                        allergy["allergenId"] as String,
                        allergy["isCleared"] as Boolean,
                        true
                    )
                }

                // Add rows for Non-IgE allergies
                for (allergy in nonIgEAllergies) {
                    addRowToTable(
                        allergyTableLayout,
                        pastAllergyTableLayout,
                        allergy["allergenName"] as String,
                        allergy["allergenId"] as String,
                        allergy["isCleared"] as Boolean,
                        false
                    )
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to load current allergies: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }


    @SuppressLint("InflateParams")
    private fun addRowToTable(allergyTableLayout: TableLayout, pastAllergyTableLayout: TableLayout, name: String, allergenId: String, isCleared: Boolean, igE: Boolean) {
        val row = layoutInflater.inflate(R.layout.table_row_item, null)
        val text = row.findViewById<TextView>(R.id.nameTextView)
        val editButton = row.findViewById<Button>(R.id.editButton)
        var igEText: String = if (igE) "IgE Allergen" else "Non-IgE Allergen"

        text.text = "$name, $igEText"

        if (isCleared) {
            editButton.text = "View"
            editButton.setOnClickListener {
                navigateToViewOnlyActivity(allergenId)
            }
            pastAllergyTableLayout.addView(row)
        } else {
            editButton.text = "Edit"
            editButton.setOnClickListener {
                navigateToEditActivity(allergenId)
            }
            allergyTableLayout.addView(row)
        }
    }


    private fun navigateToViewOnlyActivity(allergenId: String) {
        val intent = Intent(this, AddAllergiesActivity::class.java).apply {
            putExtra("allergenId", allergenId)
            putExtra("isViewOnly", true)
        }
        startActivity(intent)
    }


    private fun navigateToEditActivity(allergenId: String) {
        val intent = Intent(this, AddAllergiesActivity::class.java).apply {
            putExtra("allergenId", allergenId)
            putExtra("isViewOnly", false)
        }
        startActivity(intent)
    }


}


