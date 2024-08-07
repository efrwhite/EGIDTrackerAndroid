package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentsActivity : AppCompatActivity() {

    private lateinit var addButton: Button
    private lateinit var docsRecyclerView: RecyclerView
    private lateinit var documentUploadLauncher: ActivityResultLauncher<Intent>
    private var documentsList = mutableListOf<Document>()
    private lateinit var dbHelper: DBHelper
    private lateinit var childId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_documents)

        addButton = findViewById(R.id.addButton)
        docsRecyclerView = findViewById(R.id.docsRecyclerView)
        dbHelper = DBHelper(this)

        childId = getChildId()

        documentsList = dbHelper.getAllDocuments(childId).toMutableList()
        docsRecyclerView.layoutManager = LinearLayoutManager(this)
        docsRecyclerView.adapter = DocumentAdapter(documentsList)

        documentUploadLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null && result.data!!.data != null) {
                processDocumentUri(result.data!!.data!!)
            } else {
                Toast.makeText(this, "No file selected or an error occurred.", Toast.LENGTH_SHORT).show()
            }
        }

        addButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            documentUploadLauncher.launch(intent)
        }
    }

    private fun getChildId(): String {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return sharedPreferences.getString("CurrentChildId", "") ?: ""
    }

    private fun processDocumentUri(uri: Uri) {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val name = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val size = it.getLong(it.getColumnIndexOrThrow(OpenableColumns.SIZE))
                val date = Date()
                val type = contentResolver.getType(uri) ?: "Unknown"

                val document = Document(name, uri.toString(), type, size, date, null)
                documentsList.add(document)
                dbHelper.insertDocument(childId, name, uri.toString(), type, size, date.time, null)
                docsRecyclerView.adapter?.notifyDataSetChanged()
            }
        }
    }

    data class Document(
        val name: String,
        val url: String,
        val type: String,
        val size: Long,
        val date: Date,
        val thumbnail: String?
    )

    inner class DocumentAdapter(private val documents: List<Document>) : RecyclerView.Adapter<DocumentAdapter.DocumentViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.activity_document_item, parent, false)
            return DocumentViewHolder(view)
        }

        override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
            holder.bind(documents[position])
        }

        override fun getItemCount() = documents.size

        inner class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameTextView: TextView = itemView.findViewById(R.id.documentName)
            private val dateTextView: TextView = itemView.findViewById(R.id.documentDate)
            private val sizeTextView: TextView = itemView.findViewById(R.id.documentSize)

            init {
                itemView.setOnClickListener {
                    val document = documents[adapterPosition]
                    openDocument(document)
                }
            }

            fun bind(document: Document) {
                nameTextView.text = document.name
                dateTextView.text = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(document.date)
                sizeTextView.text = "${document.size} bytes"
            }

            private fun openDocument(document: Document) {
                val intent = Intent(Intent.ACTION_VIEW)
                val fileUri: Uri = Uri.parse(document.url) // Convert URL to Uri
                intent.setDataAndType(fileUri, document.type)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    itemView.context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(itemView.context, "No application found to open this file type.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
