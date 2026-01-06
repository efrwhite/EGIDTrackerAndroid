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

        val startDateMillis = intent.getLongExtra("startDateMillis", -1L).takeIf { it != -1L }
        val endDateMillis = intent.getLongExtra("endDateMillis", -1L).takeIf { it != -1L }

        findViewById<Button>(R.id.eoe).setOnClickListener {
            navigateToReport("eoe", childId, startDateMillis, endDateMillis)
        }

        findViewById<Button>(R.id.colon).setOnClickListener {
            navigateToReport("colon", childId, startDateMillis, endDateMillis)
        }

        findViewById<Button>(R.id.stomach).setOnClickListener {
            navigateToReport("stomach", childId, startDateMillis, endDateMillis)
        }

        findViewById<Button>(R.id.duodenum).setOnClickListener {
            navigateToReport("duodenum", childId, startDateMillis, endDateMillis)
        }
    }

    private fun navigateToReport(
        section: String,
        childId: String,
        startDateMillis: Long?,
        endDateMillis: Long?
    ) {
        val intent = Intent(this, ReportActivity::class.java).apply {
            putExtra("sourceActivity", "EndoscopyActivity")
            putExtra("section", section)
            putExtra("childId", childId)
            startDateMillis?.let { putExtra("startDateMillis", it) }
            endDateMillis?.let { putExtra("endDateMillis", it) }
        }
        startActivity(intent)
    }
}
