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

        val sourceActivity = intent.getStringExtra("sourceActivity") ?: "SymptomChecker"
        val childId = getChildId()

        loadChartData(sourceActivity, childId)

        sendReportButton.setOnClickListener {
            sendChartAsPdf()
        }
    }

    private fun getChildId(): String {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return sharedPreferences.getString("CurrentChildId", "") ?: ""
    }

    private fun loadChartData(sourceActivity: String, childId: String) {
        if (sourceActivity == "EndoscopyActivity") {
            loadEndoscopyChart(childId)
            return
        }

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

    private fun loadEndoscopyChart(childId: String) {
        Firebase.firestore.collection("Children").document(childId)
            .collection("EndoscopyResults")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(6)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val documents = querySnapshot.documents
                val dateLabels = ArrayList<String>()

                // Initialize lists to store entries for each field
                val upperEntries = ArrayList<Entry>()
                val middleEntries = ArrayList<Entry>()
                val lowerEntries = ArrayList<Entry>()
                val stomachEntries = ArrayList<Entry>()
                val duodenumEntries = ArrayList<Entry>()
                val rightColonEntries = ArrayList<Entry>()
                val middleColonEntries = ArrayList<Entry>()
                val leftColonEntries = ArrayList<Entry>()

                // Track the previous index for each line to handle missing data points
                val previousIndexMap = mutableMapOf(
                    "upper" to -1,
                    "middle" to -1,
                    "lower" to -1,
                    "stomach" to -1,
                    "duodenum" to -1,
                    "rightColon" to -1,
                    "middleColon" to -1,
                    "leftColon" to -1
                )

                // Reverse documents to display chronologically from oldest to newest
                documents.reversed().forEachIndexed { index, document ->
                    val date = document.getDate("date")
                    date?.let {
                        dateLabels.add(SimpleDateFormat("MM/dd/yyyy", Locale.US).format(it))
                    } ?: dateLabels.add("Unknown Date")

                    // Fetching data for each field
                    val upperValue = document.getLong("proximate")?.toFloat()
                    val middleValue = document.getLong("middle")?.toFloat()
                    val lowerValue = document.getLong("lower")?.toFloat()
                    val stomachValue = document.getLong("stomach")?.toFloat()
                    val duodenumValue = document.getLong("duodenum")?.toFloat()
                    val rightColonValue = document.getLong("rightColon")?.toFloat()
                    val middleColonValue = document.getLong("middleColon")?.toFloat()
                    val leftColonValue = document.getLong("leftColon")?.toFloat()

                    // Adding entries
                    upperValue?.let { addEntry(upperEntries, index, it, previousIndexMap, "upper") }
                    middleValue?.let { addEntry(middleEntries, index, it, previousIndexMap, "middle") }
                    lowerValue?.let { addEntry(lowerEntries, index, it, previousIndexMap, "lower") }
                    stomachValue?.let { addEntry(stomachEntries, index, it, previousIndexMap, "stomach") }
                    duodenumValue?.let { addEntry(duodenumEntries, index, it, previousIndexMap, "duodenum") }
                    rightColonValue?.let { addEntry(rightColonEntries, index, it, previousIndexMap, "rightColon") }
                    middleColonValue?.let { addEntry(middleColonEntries, index, it, previousIndexMap, "middleColon") }
                    leftColonValue?.let { addEntry(leftColonEntries, index, it, previousIndexMap, "leftColon") }
                }

                // Create LineDataSets for each field
                val dataSets = ArrayList<ILineDataSet>()

                // Define distinct colors for each dataset
                val colors = listOf(
                    Color.parseColor("#FF0000"), // Red
                    Color.parseColor("#0000FF"), // Blue
                    Color.parseColor("#008000"), // Green
                    Color.parseColor("#FFA500"), // Orange
                    Color.parseColor("#800080"), // Purple
                    Color.parseColor("#00FFFF"), // Cyan
                    Color.parseColor("#FFC0CB"), // Pink
                    Color.parseColor("#A52A2A")  // Brown
                )

                val fieldNames = listOf(
                    "Upper",
                    "Middle",
                    "Lower",
                    "Stomach",
                    "Duodenum",
                    "Right Colon",
                    "Middle Colon",
                    "Left Colon"
                )

                val entriesList = listOf(
                    upperEntries,
                    middleEntries,
                    lowerEntries,
                    stomachEntries,
                    duodenumEntries,
                    rightColonEntries,
                    middleColonEntries,
                    leftColonEntries
                )

                // Add data sets for each line
                for (i in entriesList.indices) {
                    if (entriesList[i].isNotEmpty()) {
                        val dataSet = LineDataSet(entriesList[i], fieldNames[i])
                        dataSet.color = colors[i]
                        dataSet.lineWidth = 2f
                        dataSet.circleRadius = 3f
                        dataSet.setCircleColor(colors[i])
                        dataSet.setDrawValues(false) // Disable point values
                        dataSets.add(dataSet as ILineDataSet)

                        // Debugging print to ensure it's being added
                        println("Adding dataset for ${fieldNames[i]} with ${entriesList[i].size} entries")
                    }
                }

                if (dataSets.isEmpty()) {
                    Toast.makeText(this, "No data available to display.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val lineData = LineData(dataSets)
                lineChart.data = lineData

                // Configure chart appearance
                lineChart.xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    valueFormatter = IndexAxisValueFormatter(dateLabels)
                    setDrawGridLines(false)
                    labelRotationAngle = 45f // Rotate labels to prevent overlap
                }

                val yAxis = lineChart.axisLeft
                yAxis.granularity = 1f // Set the granularity to 1, which means whole numbers
                yAxis.isGranularityEnabled = true // Ensure granularity is enabled


                // Increase bottom padding to give space for the legend
                lineChart.setExtraOffsets(10f, 0f, 30f, 10f) // Adjust this if legend still cuts off

                lineChart.axisLeft.setDrawGridLines(false)
                lineChart.axisRight.isEnabled = false
                lineChart.description.isEnabled = false

                // Customize legend appearance
                val legend = lineChart.legend
                legend.isWordWrapEnabled = true // Enable word wrapping
                legend.setMaxSizePercent(0.95f) // Ensure legend occupies no more than 95% of the width
                legend.xEntrySpace = 10f // Space between items on the x-axis
                legend.yEntrySpace = 5f // Space between items on the y-axis
                legend.xOffset = 10f
                legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER


                // Refresh the chart
                lineChart.notifyDataSetChanged()  // Ensure chart is aware of new data
                lineChart.invalidate()            // Redraw the chart
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
