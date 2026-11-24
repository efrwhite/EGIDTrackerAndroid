package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/** The selected child's current and cleared allergens, IgE allergens first. */
class AllergiesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_allergies)
        setupPageHeader(R.string.allergens)

        findViewById<View>(R.id.addAllergenButton).setOnClickListener {
            startActivity(Intent(this, AddAllergiesActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadAllergens() // also picks up changes made in AddAllergiesActivity
    }

    private fun loadAllergens() {
        val childId = CurrentChild.getId(this) ?: return
        Firebase.firestore.collection("Allergens")
            .whereEqualTo("childId", childId)
            .get()
            .addOnSuccessListener { documents ->
                val sorted = documents.sortedWith(
                    compareByDescending<QueryDocumentSnapshot> { it.getBoolean("igE") ?: false }
                        .thenBy { it.getString("allergenName")?.lowercase() }
                )
                val current = mutableListOf<ListRow>()
                val cleared = mutableListOf<ListRow>()
                for (doc in sorted) {
                    val name = doc.getString("allergenName").orEmpty().ifBlank { "No name" }
                    val type = getString(if (doc.getBoolean("igE") == true) R.string.ige_allergen else R.string.non_ige_allergen)
                    if (doc.getBoolean("cleared") == true) {
                        cleared += ListRow(name, type, getString(R.string.view)) { open(doc.id, viewOnly = true) }
                    } else {
                        current += ListRow(name, type, getString(R.string.edit)) { open(doc.id, viewOnly = false) }
                    }
                }
                fillList(findViewById<LinearLayout>(R.id.currentRows), findViewById(R.id.currentEmpty), current)
                fillList(findViewById<LinearLayout>(R.id.pastRows), findViewById(R.id.pastEmpty), cleared)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load allergens: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun open(allergenId: String, viewOnly: Boolean) {
        startActivity(Intent(this, AddAllergiesActivity::class.java).apply {
            putExtra("allergenId", allergenId)
            putExtra("isViewOnly", viewOnly)
        })
    }
}
