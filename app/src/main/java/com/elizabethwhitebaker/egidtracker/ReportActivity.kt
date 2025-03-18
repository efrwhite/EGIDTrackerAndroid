package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class ReportActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var descriptionText: TextView
    private lateinit var sendReportButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        lineChart = findViewById(R.id.lineChart)
        descriptionText = findViewById(R.id.descriptionText)
        sendReportButton = findViewById(R.id.sendButton)

        sendReportButton.setOnClickListener {
            sendChartAsPdf()
        }

        val sourceActivity = intent.getStringExtra("sourceActivity") ?: "SymptomChecker"
        val section = intent.getStringExtra("section") ?: "proximate"  // default section
        val childId = getChildId()

        if (sourceActivity == "EndoscopyActivity") {
            loadEndoscopyChart(childId, section)
        } else {
            loadChartData(sourceActivity, childId)
        }
    }

    private fun getChildId(): String {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return sharedPreferences.getString("CurrentChildId", "") ?: ""
    }

    private fun loadChartData(sourceActivity: String, childId: String) {
        val subCollection = when (sourceActivity) {
            "SymptomChecker" -> "Symptom Scores"
            "QoLActivity" -> "Quality of Life Scores"
            else -> "Symptom Scores"
        }

        Firebase.firestore.collection("Children").document(childId)
            .collection(subCollection)
            .orderBy("date", Query.Direction.ASCENDING)
            .limit(6)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val documents = querySnapshot.documents
                val entries = ArrayList<Entry>()
                val dateLabels = ArrayList<String>()

                documents.forEachIndexed { index, document ->
                    val score = document.getLong("totalScore")?.toFloat() ?: 0f
                    val date = document.getDate("date")
                    entries.add(Entry(index.toFloat(), score))
                    date?.let {
                        dateLabels.add(SimpleDateFormat("MM/dd/yyyy", Locale.US).format(it))
                    }
                }

                val dataSet = LineDataSet(entries, "Score Over Time").apply {
                    color = ColorTemplate.getHoloBlue()
                    valueTextColor = Color.BLACK
                    valueTextSize = 12f
                }

                lineChart.data = LineData(dataSet)
                lineChart.xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    valueFormatter = IndexAxisValueFormatter(dateLabels)
                    setDrawGridLines(false)
                }
                lineChart.axisLeft.setDrawGridLines(false)
                lineChart.axisRight.isEnabled = false
                lineChart.description.isEnabled = false
                lineChart.invalidate() // Refresh the graph

                updateDescriptions(documents, sourceActivity)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadEndoscopyChart(childId: String, section: String) {
        Firebase.firestore.collection("Children").document(childId)
            .collection("EndoscopyResults")
            .orderBy("date", Query.Direction.ASCENDING) // Ensure chronological order
            .get()
            .addOnSuccessListener { querySnapshot ->
                val documents = querySnapshot.documents
                val dateLabels = ArrayList<String>()

                // Define mappings for multiple graph lines per section
                val fieldMap = when (section) {
                    "eoe" -> listOf("proximate", "middle", "lower")
                    "colon" -> listOf("rightColon", "middleColon", "leftColon")
                    "stomach" -> listOf("stomach")
                    "duodenum" -> listOf("duodenum")
                    else -> emptyList()
                }

                if (fieldMap.isEmpty()) return@addOnSuccessListener

                // Create entry lists for each field
                val dataEntriesMap = mutableMapOf<String, ArrayList<Entry>>()
                for (field in fieldMap) {
                    dataEntriesMap[field] = ArrayList()
                }

                // ✅ Sort documents chronologically
                val sortedDocuments = documents.sortedBy { it.getDate("date") }

                sortedDocuments.forEachIndexed { index, document ->
                    val date = document.getDate("date")
                    date?.let {
                        dateLabels.add(SimpleDateFormat("MM/dd/yyyy", Locale.US).format(it))
                    } ?: dateLabels.add("Unknown Date")

                    // ✅ Ensure each field has a value (default to `0` if missing)
                    for (field in fieldMap) {
                        val fieldValue = document.getLong(field)?.toFloat() ?: 0f
                        dataEntriesMap[field]?.add(Entry(index.toFloat(), fieldValue))
                    }
                }

                if (dataEntriesMap.values.all { it.isEmpty() }) {
                    Toast.makeText(this, "No data available for this section.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Define distinct colors for each dataset
                val colors = listOf(
                    Color.RED, // First field
                    Color.BLUE, // Second field
                    Color.GREEN, // Third field
                    Color.MAGENTA, // Fourth field if needed
                    Color.CYAN // Fifth field if needed
                )

                val dataSets = ArrayList<ILineDataSet>()

                // ✅ Ensure all fields are represented in the legend, even if values are 0
                fieldMap.forEachIndexed { index, field ->
                    val entries = dataEntriesMap[field] ?: arrayListOf()
                    if (entries.isEmpty()) {
                        // Ensure the dataset exists with zeroed-out entries if missing
                        for (i in dateLabels.indices) {
                            entries.add(Entry(i.toFloat(), 0f))
                        }
                    }

                    val dataSet = LineDataSet(entries, field.capitalize()).apply {
                        color = colors[index % colors.size]
                        valueTextColor = Color.BLACK
                        valueTextSize = 12f
                        setDrawValues(true) // Ensure numbers are shown at each point
                        setCircleColor(colors[index % colors.size])
                        setDrawCircles(true)
                        circleRadius = 4f
                    }
                    dataSets.add(dataSet)
                }

                val lineData = LineData(dataSets)
                lineChart.data = lineData

                // Configure chart appearance
                lineChart.xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    valueFormatter = IndexAxisValueFormatter(dateLabels)
                    setDrawGridLines(false)
                }

                lineChart.axisLeft.setDrawGridLines(false)
                lineChart.axisRight.isEnabled = false
                lineChart.description.isEnabled = false

                // ✅ Customize legend appearance for multiple lines
                val legend = lineChart.legend
                legend.isWordWrapEnabled = true
                legend.setMaxSizePercent(0.95f)
                legend.xEntrySpace = 10f
                legend.yEntrySpace = 5f
                legend.xOffset = 10f
                legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER

                lineChart.invalidate()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }



    private fun addEntry(entries: ArrayList<Entry>, currentIndex: Int, value: Float, previousIndexMap: MutableMap<String, Int>, fieldName: String) {
        if (previousIndexMap[fieldName] != -1) {
            // Add a point to connect to the previous valid point if it exists
            entries.add(Entry(currentIndex.toFloat(), value))
        } else {
            // Only add the new point if there was no previous valid point
            entries.add(Entry(currentIndex.toFloat(), value))
        }
        // Update the previous index for this field
        previousIndexMap[fieldName] = currentIndex
    }



    private fun updateDescriptions(documents: List<DocumentSnapshot>, sourceActivity: String) {
        if (sourceActivity == "SymptomChecker") {
            // Only update description text for SymptomChecker
            val mostRecentWithY = documents.firstOrNull { doc ->
                (doc["responses"] as? List<*>)?.contains("y") == true
            }

            mostRecentWithY?.let { doc ->
                val descriptions = doc["symptomDescriptions"] as? List<*>
                descriptionText.text = descriptions?.joinToString(", ") ?: "No descriptions available"
            }
        }
    }

    private fun captureView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun sendChartAsPdf() {
        val bitmap = captureView(findViewById(R.id.chartContainer))
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        val canvas = page.canvas
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        val pdfFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "report.pdf")
        pdfDocument.writeTo(FileOutputStream(pdfFile))
        pdfDocument.close()

        // Share the PDF file using FileProvider
        val uri = FileProvider.getUriForFile(this, "com.elizabethwhitebaker.egidtracker.fileprovider", pdfFile)
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "application/pdf"
        }
        startActivity(Intent.createChooser(shareIntent, "Send Report"))
    }

}
