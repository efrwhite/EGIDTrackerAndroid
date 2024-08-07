package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth


class MainActivity : AppCompatActivity() {
        private lateinit var auth: FirebaseAuth

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_landing)

            auth = FirebaseAuth.getInstance()

            val signUp = findViewById<Button>(R.id.signUpButton)
            val signIn = findViewById<Button>(R.id.signInButton)

            // Set click listeners for Sign Up and Log In buttons
            signUp.setOnClickListener {
                // Navigate to the Sign Up activity
                startActivity(Intent(this, SignUpActivity::class.java))
            }

            signIn.setOnClickListener {
                // Navigate to the Log In activity
                startActivity(Intent(this, SignInActivity::class.java))
            }
        }
    }

