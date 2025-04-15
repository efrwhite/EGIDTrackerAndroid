package com.elizabethwhitebaker.egidtracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class PlanActivity : AppCompatActivity() {

    private lateinit var dietButton: Button
    private lateinit var accidentalExposureButton: Button
    private lateinit var medicationsButton: Button
    private lateinit var documentsButton: Button
    private lateinit var endoscopyButton: Button
    private lateinit var childDiet: String
    private lateinit var planName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_yourplan)

        //Initialize buttons
        dietButton = findViewById(R.id.dietButton)
        accidentalExposureButton = findViewById(R.id.accidentalExposureButton)
        medicationsButton = findViewById(R.id.medicationsButton)
        documentsButton = findViewById(R.id.documentsButton)
        endoscopyButton = findViewById(R.id.endoscopyButton)
        planName = findViewById(R.id.planName)


        val childId = getCurrentChildId()
        childId?.let {
            fetchChildDiet(it)
        }
        //set onClickListeners for Buttons
        dietButton.setOnClickListener {
            goToDietActivity()
        }

        accidentalExposureButton.setOnClickListener {
            val intent = Intent(this, AccidentalExposureActivity :: class.java)
            startActivity(intent)
        }

        medicationsButton.setOnClickListener {
            val intent = Intent(this, MedicationsActivity :: class.java)
            startActivity(intent)
        }

        documentsButton.setOnClickListener {
            val intent = Intent(this, DocumentsActivity :: class.java)
            startActivity(intent)
        }

        endoscopyButton.setOnClickListener {
            val intent = Intent(this, EndoscopyActivity :: class.java)
            startActivity(intent)
        }
    }

    private fun fetchChildDiet(childId: String) {
        val db = Firebase.firestore
        db.collection("Children").document(childId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    childDiet = document.getString("diet") ?: "No Diet Specified"
                    val childName = document.getString("firstName") ?: "No Name"

                    // Update UI with child data
                    planName.text = childName

                } else {
                    Toast.makeText(this, "Diet not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load child data: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun getCurrentChildId(): String? {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("CurrentChildId", null)
    }

    private fun goToDietActivity(){
        val intent: Intent
        if(childDiet == "Diet 1"){
            intent = Intent(this, Diet1Activity::class.java)
        } else if (childDiet == "Diet 2") {
            intent = Intent(this, Diet2Activity::class.java)
        } else if (childDiet == "Diet 4"){
            intent = Intent(this, Diet4Activity::class.java)
        } else if (childDiet == "Diet 6"){
            intent = Intent(this, Diet6Activity::class.java)
        } else if (childDiet == "None"){
            intent = Intent(this, DietNoneActivity::class.java)
        } else {
            throw IllegalArgumentException("Invalid diet")
        }
        startActivity(intent)
    }

}