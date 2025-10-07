package com.elizabethwhitebaker.egidtracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class PlanActivity : AppCompatActivity() {

    private lateinit var dietButton: Button
    private lateinit var accidentalExposureButton: Button
    private lateinit var medicationsButton: Button
    private lateinit var documentsButton: Button
    private lateinit var endoscopyButton: Button
    private lateinit var planName: TextView

    private var childDiet: String? = null  // set after fetch

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { Firebase.firestore }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_yourplan)

        dietButton = findViewById(R.id.dietButton)
        accidentalExposureButton = findViewById(R.id.accidentalExposureButton)
        medicationsButton = findViewById(R.id.medicationsButton)
        documentsButton = findViewById(R.id.documentsButton)
        endoscopyButton = findViewById(R.id.endoscopyButton)
        planName = findViewById(R.id.planName)

        // initial fetch
        getCurrentChildId()?.let { fetchChildDiet(it) }

        dietButton.setOnClickListener {
            val diet = childDiet?.trim().orEmpty()
            if (diet.isEmpty()) {
                Toast.makeText(this, "No diet is selected for Child", Toast.LENGTH_SHORT).show()
            } else {
                goToDietActivity(diet)
            }
        }

        accidentalExposureButton.setOnClickListener {
            startActivity(Intent(this, AccidentalExposureActivity::class.java))
        }
        medicationsButton.setOnClickListener {
            startActivity(Intent(this, MedicationsActivity::class.java))
        }
        documentsButton.setOnClickListener {
            startActivity(Intent(this, DocumentsActivity::class.java))
        }
        endoscopyButton.setOnClickListener {
            startActivity(Intent(this, EndoscopyActivity::class.java))
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
    private fun getCurrentChildId(): String? {
        val uid = auth.currentUser?.uid ?: return null
        val sp = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return sp.getString("CurrentChildId_$uid", null)
    }

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
