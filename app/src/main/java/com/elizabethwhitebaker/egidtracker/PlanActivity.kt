package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class PlanActivity : AppCompatActivity() {

    private lateinit var planName: TextView

    private var childDiet: String? = null  // set after fetch

    private val db by lazy { Firebase.firestore }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_yourplan)

        planName = findViewById(R.id.planName)

        // initial fetch
        getCurrentChildId()?.let { fetchChildDiet(it) }

        findViewById<View>(R.id.dietButton).setOnClickListener {
            val diet = childDiet?.trim().orEmpty()
            if (diet.isEmpty()) {
                Toast.makeText(this, "No diet is selected for Child", Toast.LENGTH_SHORT).show()
            } else {
                goToDietActivity(diet)
            }
        }

        mapOf(
            R.id.accidentalExposureButton to AccidentalExposureActivity::class.java,
            R.id.medicationsButton to MedicationsActivity::class.java,
            R.id.documentsButton to DocumentsActivity::class.java,
            R.id.endoscopyButton to EndoscopyActivity::class.java
        ).forEach { (buttonId, screen) ->
            findViewById<View>(buttonId).setOnClickListener { startActivity(Intent(this, screen)) }
        }
    }

    override fun onResume() {
        super.onResume()
        // re-fetch so changes made elsewhere are reflected here
        getCurrentChildId()?.let { fetchChildDiet(it) }
    }

    private fun fetchChildDiet(childId: String) {
        db.collection("Children").document(childId).get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    Toast.makeText(this, "Diet not found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val firstName = (document.getString("firstName") ?: "No Name").trim()
                planName.text = firstName

                // Keep the raw string but normalize later when routing
                childDiet = (document.getString("diet") ?: "").trim()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load child data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Read the namespaced key (per-user)
    private fun getCurrentChildId(): String? = CurrentChild.getId(this)

    private fun goToDietActivity(dietRaw: String) {
        val diet = dietRaw.trim().lowercase()

        val intent = when (diet) {
            "diet 1" -> Intent(this, Diet1Activity::class.java)
            "diet 2" -> Intent(this, Diet2Activity::class.java)
            "diet 4" -> Intent(this, Diet4Activity::class.java)
            "diet 6" -> Intent(this, Diet6Activity::class.java)
            "no diet", "none", "" -> Intent(this, DietNoneActivity::class.java)
            else -> {
                Toast.makeText(this, "Invalid diet: $dietRaw", Toast.LENGTH_SHORT).show()
                return
            }
        }
        startActivity(intent)
    }
}
