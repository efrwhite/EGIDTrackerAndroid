package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class EducationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_education)

        // Each question button opens the screen that answers it
        mapOf(
            R.id.whatEoEButton to WhatIsEoEActivity::class.java,
            R.id.causesEoEButton to EoECausesActivity::class.java,
            R.id.affectedEoEButton to WhoIsAffectedActivity::class.java,
            R.id.symptomsEoEButton to SymptomsActivity::class.java,
            R.id.diagnosedEoEButton to DiagnosedActivity::class.java,
            R.id.treatedEoEButton to TreatmentActivity::class.java,
            R.id.moreInfoButton to MoreInfoActivity::class.java
        ).forEach { (buttonId, screen) ->
            findViewById<View>(buttonId).setOnClickListener { startActivity(Intent(this, screen)) }
        }
    }
}
