package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class EducationActivity : AppCompatActivity() {

    private lateinit var whatEoEButton: Button
    private lateinit var causesEoEButton: Button
    private lateinit var affectedEoEButton: Button
    private lateinit var symptomsEoEButton: Button
    private lateinit var diagnosedEoEButton: Button
    private lateinit var treatedEoEButton: Button
    private lateinit var moreInfoButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_education)

        //initialize buttons
        whatEoEButton = findViewById(R.id.whatEoEButton)
        causesEoEButton = findViewById(R.id.causesEoEButton)
        affectedEoEButton = findViewById(R.id.affectedEoEButton)
        symptomsEoEButton = findViewById(R.id.symptomsEoEButton)
        diagnosedEoEButton = findViewById(R.id.diagnosedEoEButton)
        treatedEoEButton = findViewById(R.id.treatedEoEButton)
        moreInfoButton = findViewById(R.id.moreInfoButton)

        //set onClickListeners for buttons
        whatEoEButton.setOnClickListener {
            val intent = Intent(this, WhatIsEoEActivity::class.java)
            startActivity(intent)
        }

        causesEoEButton.setOnClickListener {
            val intent = Intent(this, EoECausesActivity::class.java)
            startActivity(intent)
        }

        affectedEoEButton.setOnClickListener {
            val intent = Intent(this, WhoIsAffectedActivity::class.java)
            startActivity(intent)
        }

        symptomsEoEButton.setOnClickListener {
            val intent = Intent(this, SymptomsActivity::class.java)
            startActivity(intent)
        }

        diagnosedEoEButton.setOnClickListener {
            val intent = Intent(this, DiagnosedActivity::class.java)
            startActivity(intent)
        }

        treatedEoEButton.setOnClickListener {
            val intent = Intent(this, TreatmentActivity::class.java)
            startActivity(intent)
        }

        moreInfoButton.setOnClickListener {
            val intent = Intent(this, MoreInfoActivity::class.java)
            startActivity(intent)
        }


    }

}