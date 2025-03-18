package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class EndoscopyButtonsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_endoscopy_buttons)

        val childId = intent.getStringExtra("childId") ?: return

        findViewById<Button>(R.id.eoe).setOnClickListener {
            navigateToReport("eoe", childId)
        }

        findViewById<Button>(R.id.colon).setOnClickListener {
            navigateToReport("colon", childId)
        }

        findViewById<Button>(R.id.stomach).setOnClickListener {
            navigateToReport("stomach", childId)
        }

        findViewById<Button>(R.id.duodenum).setOnClickListener {
            navigateToReport("duodenum", childId)
        }
    }

    private fun navigateToReport(section: String, childId: String) {
        val intent = Intent(this, ReportActivity::class.java).apply {
            putExtra("sourceActivity", "EndoscopyActivity")
            putExtra("section", section) // Pass selected section
            putExtra("childId", childId)
        }
        startActivity(intent)
    }
}
