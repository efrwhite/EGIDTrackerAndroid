package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth


class MainActivity : AppCompatActivity() {
        private lateinit var auth: FirebaseAuth

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_landing)

            // Draw the background edge-to-edge (behind the status/nav bars); their
            // color/icon appearance and hiding the ActionBar are set via
            // Theme.EGIDTracker.Landing in the manifest instead of here.
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val landingRoot = findViewById<View>(R.id.landingRoot)
            ViewCompat.setOnApplyWindowInsetsListener(landingRoot) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }

            auth = FirebaseAuth.getInstance()

            val signUp = findViewById<Button>(R.id.signUpButton)
            val signIn = findViewById<Button>(R.id.signInButton)

            // Set click listeners for Register and Login buttons
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

