package com.elizabethwhitebaker.egidtracker

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Chart of the selected child's scores over time, which can be sent as a PDF. Extras:
 * sourceActivity (SymptomChecker, QoLActivity or EndoscopyActivity), section (endoscopy only:
 * eoe, colon, stomach or duodenum), and optional startDateMillis / endDateMillis.
 */
class ReportActivity : AppCompatActivity() {

    companion object {
        /** Questionnaire reports show this many of the most recent scores in the range. */
        private const val MAX_SCORES = 6

        /** Endoscopy sections: the Firestore field and label of each site charted. */
        private val ENDOSCOPY_SITES = mapOf(
            "eoe" to listOf("proximate" to R.string.upper, "middle" to R.string.middle, "lower" to R.string.lower),
            "colon" to listOf("rightColon" to R.string.right, "middleColon" to R.string.middle, "leftColon" to R.string.left),
            "stomach" to listOf("stomach" to R.string.stomach),
            "duodenum" to listOf("duodenum" to R.string.duodenum),
        )
        private val SECTION_NAMES = mapOf(
            "eoe" to R.string.eoe, "colon" to R.string.colon, "stomach" to R.string.stomach, "duodenum" to R.string.duodenum
        )
    }

    private lateinit var lineChart: LineChart
    private lateinit var descriptionText: TextView

    private val sourceActivity by lazy { intent.getStringExtra("sourceActivity") ?: "SymptomChecker" }
    private val section by lazy { intent.getStringExtra("section") ?: "eoe" }
    private val dateFormat = SimpleDateFormat(DateField.DISPLAY_PATTERN, Locale.US)
    private val axisDateFormat = SimpleDateFormat("MM/dd/yy", Locale.US) // six full dates don't fit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)
        when (sourceActivity) {
            "EndoscopyActivity" -> setupPageHeader(R.string.report_endoscopy)
            "QoLActivity" -> setupPageHeader(R.string.report_qol)
            else -> setupPageHeader(R.string.report_symptoms)
        }
        if (sourceActivity == "EndoscopyActivity") {
            findViewById<TextView>(R.id.pageTitle)?.text =
                getString(R.string.report_endoscopy, getString(SECTION_NAMES[section] ?: R.string.eoe))
        }
        val childId = requireSelectedChild() ?: return // needs a child; offers to add one

        lineChart = findViewById(R.id.lineChart)
        descriptionText = findViewById(R.id.descriptionText)
        setupChart()
        findViewById<View>(R.id.sendButton).setOnClickListener { sendChartAsPdf() }

        loadScores(childId)
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun setupChart() = lineChart.apply {
        description.isEnabled = false
        setNoDataText(getString(R.string.report_no_scores))
        setNoDataTextColor(color(R.color.mutedPurple))
        axisRight.isEnabled = false
        axisLeft.apply {
            setDrawGridLines(false)
            axisMinimum = 0f
            textColor = color(R.color.mutedPurple)
            valueFormatter = WholeNumbers
        }
        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            // Half a step of room at each end, so the first and last dates aren't cut off
            spaceMin = 0.5f
            spaceMax = 0.5f
            textColor = color(R.color.mutedPurple)
        }
        legend.apply {
            isWordWrapEnabled = true
            horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            textColor = color(R.color.nearBlack)
        }
    }

    /** Scores are whole numbers; the chart library would otherwise show "30.0". */
    private object WholeNumbers : ValueFormatter() {
        override fun getFormattedValue(value: Float) = value.toInt().toString()
    }

    private fun loadScores(childId: String) {
        val subCollection = when (sourceActivity) {
            "QoLActivity" -> "Quality of Life Scores"
            "EndoscopyActivity" -> "EndoscopyResults"
            else -> "Symptom Scores"
        }
        // Newest first, so a limit keeps the most recent; reversed below for the chart
        var query: Query = Firebase.firestore.collection("Children").document(childId)
            .collection(subCollection)
            .orderBy("date", Query.Direction.DESCENDING)
        intent.getLongExtra("startDateMillis", -1L).takeIf { it != -1L }?.let {
            query = query.whereGreaterThanOrEqualTo("date", Timestamp(Date(it)))
        }
        intent.getLongExtra("endDateMillis", -1L).takeIf { it != -1L }?.let { end ->
            // Inclusive through the end of that day
            val endOfDay = Calendar.getInstance().apply {
                timeInMillis = end
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }.time
            query = query.whereLessThanOrEqualTo("date", Timestamp(endOfDay))
        }
        if (sourceActivity != "EndoscopyActivity") query = query.limit(MAX_SCORES.toLong())

        query.get()
            .addOnSuccessListener { snapshot ->
                // Skips records without a proper date, rather than failing on them
                val scores = snapshot.documents
                    .mapNotNull { doc -> (doc.get("date") as? Timestamp)?.let { doc to it.toDate() } }
                    .reversed()
                if (scores.isEmpty()) {
                    lineChart.clear() // shows the "no scores" text
                    return@addOnSuccessListener
                }
                showChart(scores)
                if (sourceActivity == "SymptomChecker") showLatestSymptoms(scores)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showChart(scores: List<Pair<DocumentSnapshot, Date>>) {
        val series = if (sourceActivity == "EndoscopyActivity") {
            ENDOSCOPY_SITES[section].orEmpty().map { (field, label) -> getString(label).trimEnd(':') to field }
        } else {
            listOf(getString(R.string.report_score_label) to "totalScore")
        }
        val palette = listOf(R.color.primaryBackground, R.color.lightSalmon, R.color.softPurple).map(::color)

        val dataSets = series.mapIndexed { i, (label, field) ->
            val entries = scores.mapIndexed { x, (doc, _) -> Entry(x.toFloat(), doc.getLong(field)?.toFloat() ?: 0f) }
            LineDataSet(entries, label).apply {
                val lineColor = palette[i % palette.size]
                color = lineColor
                setCircleColor(lineColor)
                circleRadius = 4f
                lineWidth = 2f
                valueTextSize = 12f
                valueTextColor = color(R.color.nearBlack)
                valueFormatter = WholeNumbers
            }
        }
        lineChart.data = LineData(dataSets)
        lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(scores.map { (_, date) -> axisDateFormat.format(date) })
        lineChart.xAxis.labelCount = scores.size
        lineChart.invalidate()
    }

    /** The yes/no symptoms of the most recent score that had any. */
    private fun showLatestSymptoms(scores: List<Pair<DocumentSnapshot, Date>>) {
        val latest = scores.lastOrNull { (doc, _) ->
            (doc.get("symptomDescriptions") as? List<*>).orEmpty().isNotEmpty()
        }
        descriptionText.visibility = View.VISIBLE
        descriptionText.text = latest?.let { (doc, date) ->
            val symptoms = (doc.get("symptomDescriptions") as List<*>).joinToString(", ")
            getString(R.string.report_latest_symptoms, dateFormat.format(date), symptoms)
        } ?: getString(R.string.report_no_symptoms)
    }

    private fun captureView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        view.draw(canvas)
        return bitmap
    }

    private fun sendChartAsPdf() {
        val bitmap = captureView(findViewById(R.id.chartContainer))
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        val pdfFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "report.pdf")
        FileOutputStream(pdfFile).use { pdfDocument.writeTo(it) }
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(this, "com.elizabethwhitebaker.egidtracker.fileprovider", pdfFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "application/pdf"
            // Without these, the receiving app (and the share sheet's preview) can't open the file
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.send_report)))
    }
}
