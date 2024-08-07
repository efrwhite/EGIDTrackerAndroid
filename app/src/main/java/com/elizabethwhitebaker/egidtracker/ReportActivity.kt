package com.elizabethwhitebaker.egidtracker

/**
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
*/

import androidx.appcompat.app.AppCompatActivity

class ReportActivity : AppCompatActivity() {
    /**

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
            sendChartAsImage()
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
            .orderBy("date", Query.Direction.DESCENDING)
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

                updateDescriptions(documents)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateDescriptions(documents: List<DocumentSnapshot>) {
        val mostRecentWithY = documents.firstOrNull { doc ->
            (doc["responses"] as? List<*>)?.contains("y") == true
        }

        mostRecentWithY?.let { doc ->
            val descriptions = doc["symptomDescriptions"] as? List<*>
            descriptionText.text = descriptions?.joinToString(", ") ?: "No descriptions available"
        }
    }
    private fun captureView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun sendChartAsImage() {
        val bitmap = captureView(findViewById(R.id.chartContainer))

        // Save the bitmap to a file
        val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "report.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Share the file using FileProvider
        val uri = FileProvider.getUriForFile(this, "com.elizabethwhitebaker.egidtracker.fileprovider", file)
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "image/png"
        }
        startActivity(Intent.createChooser(shareIntent, "Send Report"))
    }
*/
}


