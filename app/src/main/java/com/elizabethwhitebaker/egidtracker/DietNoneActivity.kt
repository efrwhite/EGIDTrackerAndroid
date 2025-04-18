package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class DietNoneActivity : AppCompatActivity() {
    private lateinit var allergiesButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diet_none)

        //initialize buttons
        allergiesButton = findViewById(R.id.allergiesButton)

        // Set onClickListeners for the buttons
        allergiesButton.setOnClickListener {
            val intent = Intent(this, AllergiesActivity::class.java)
            startActivity(intent)
        }
    }
}