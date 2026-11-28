package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * Chooses which part of the endoscopy results to chart, then opens ReportActivity for it,
 * passing on the date range chosen in Results (startDateMillis / endDateMillis).
 */
class EndoscopyButtonsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_endoscopy_buttons)
        setupPageHeader(R.string.endoscopy_report_title)
        val childId = requireSelectedChild() ?: return // needs a child; offers to add one

        // Section (as ReportActivity expects it), its name, and what it charts
        val sections = listOf(
            Triple("eoe", R.string.eoe, R.string.section_eoe_detail),
            Triple("colon", R.string.colon, R.string.section_colon_detail),
            Triple("stomach", R.string.stomach, null),
            Triple("duodenum", R.string.duodenum, null),
        )
        val rows = sections.map { (section, name, detail) ->
            ListRow(getString(name), detail?.let(::getString), getString(R.string.view)) {
                openReport(section, childId)
            }
        }
        fillList(findViewById<LinearLayout>(R.id.sectionRows), findViewById(R.id.sectionsEmpty), rows)
    }

    private fun openReport(section: String, childId: String) {
        startActivity(Intent(this, ReportActivity::class.java).apply {
            putExtra("sourceActivity", "EndoscopyActivity")
            putExtra("section", section)
            putExtra("childId", childId)
            intent.getLongExtra("startDateMillis", -1L).takeIf { it != -1L }?.let { putExtra("startDateMillis", it) }
            intent.getLongExtra("endDateMillis", -1L).takeIf { it != -1L }?.let { putExtra("endDateMillis", it) }
        })
    }
}
