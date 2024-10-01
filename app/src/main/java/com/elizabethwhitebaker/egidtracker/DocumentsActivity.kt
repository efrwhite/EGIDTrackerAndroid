package com.elizabethwhitebaker.egidtracker

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Date

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

                val document = Document(0, name, uri.toString(), type, size, date, null)
                val id = dbHelper.insertDocument(childId, name, uri.toString(), type, size, date.time, null)
                documentsList.add(document)
                dbHelper.insertDocument(childId, name, uri.toString(), type, size, date.time, null)
                docsRecyclerView.adapter?.notifyDataSetChanged()
            }
        }
    }

    class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        companion object {
            private const val DATABASE_NAME = "documents.db"
            private const val DATABASE_VERSION = 1

            private const val TABLE_DOCUMENTS = "documents"
            private const val COLUMN_ID = "id"
            private const val COLUMN_NAME = "name"
            private const val COLUMN_URL = "url"
            private const val COLUMN_TYPE = "type"
            private const val COLUMN_SIZE = "size"
            private const val COLUMN_DATE = "date"
            private const val COLUMN_THUMBNAIL = "thumbnail"
        }

        override fun onCreate(db: SQLiteDatabase?) {
            val createTable = ("CREATE TABLE $TABLE_DOCUMENTS ("
                    + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "$COLUMN_NAME TEXT, "
                    + "$COLUMN_URL TEXT, "
                    + "$COLUMN_TYPE TEXT, "
                    + "$COLUMN_SIZE INTEGER, "
                    + "$COLUMN_DATE INTEGER, "
                    + "$COLUMN_THUMBNAIL TEXT)")
            db?.execSQL(createTable)
        }

        override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
            db?.execSQL("DROP TABLE IF EXISTS $TABLE_DOCUMENTS")
            onCreate(db)
        }

        fun insertDocument(childId: String, name: String, url: String, type: String, size: Long, date: Long, thumbnail: String?) {
            val db = this.writableDatabase
            val contentValues = ContentValues().apply {
                put(COLUMN_NAME, name)
                put(COLUMN_URL, url)
                put(COLUMN_TYPE, type)
                put(COLUMN_SIZE, size)
                put(COLUMN_DATE, date)
                put(COLUMN_THUMBNAIL, thumbnail)
            }
            db.insert(TABLE_DOCUMENTS, null, contentValues)
            db.close()
        }

        fun getAllDocuments(childId: String): List<Document> {
            val documents = mutableListOf<Document>()
            val db = this.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM $TABLE_DOCUMENTS", null)
            cursor?.use {
                if (it.moveToFirst()) {
                    do {
                        val id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID))
                        val name = it.getString(it.getColumnIndexOrThrow(COLUMN_NAME))
                        val url = it.getString(it.getColumnIndexOrThrow(COLUMN_URL))
                        val type = it.getString(it.getColumnIndexOrThrow(COLUMN_TYPE))
                        val size = it.getLong(it.getColumnIndexOrThrow(COLUMN_SIZE))
                        val date = Date(it.getLong(it.getColumnIndexOrThrow(COLUMN_DATE)))
                        val thumbnail = it.getString(it.getColumnIndexOrThrow(COLUMN_THUMBNAIL))
                        documents.add(Document(id, name, url, type, size, date, thumbnail))
                    } while (it.moveToNext())
                }
            }
            db.close()
            return documents
        }

        fun updateDocumentName(documentId: Long, newName: String) {
            val db = this.writableDatabase
            val contentValues = ContentValues().apply {
                put(COLUMN_NAME, newName)
            }
            db.update(TABLE_DOCUMENTS, contentValues, "$COLUMN_ID = ?", arrayOf(documentId.toString()))
            db.close()
        }

        fun deleteDocument(documentId: Long) {
            val db = this.writableDatabase
            db.delete(TABLE_DOCUMENTS, "$COLUMN_ID = ?", arrayOf(documentId.toString()))
            db.close()
        }
    }


    data class Document(
        var id: Long,
        var name: String,
        val url: String,
        val type: String,
        val size: Long,
        val date: Date,
        val thumbnail: String?

    )

    inner class DocumentAdapter(private val documents: List<Document>) : RecyclerView.Adapter<DocumentAdapter.DocumentViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.table_row_item, parent, false)
            return DocumentViewHolder(view)
        }

        override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
            holder.bind(documents[position])
        }

        override fun getItemCount() = documents.size

        inner class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
            private val editButton: Button = itemView.findViewById(R.id.editButton)

            init {
                itemView.setOnClickListener {
                    val document = documents[adapterPosition]
                    openDocument(document)
                }
                editButton.setOnClickListener {
                    val document = documents[adapterPosition]
                    showEditOptionsDialog(document)
                }
            }

            fun bind(document: Document) {
                nameTextView.text = document.name
                editButton.visibility = View.VISIBLE // Ensure the edit button is visible
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

            private fun showEditOptionsDialog(document: Document) {
                val options = arrayOf("Rename", "Delete")
                AlertDialog.Builder(itemView.context)
                    .setTitle("Edit Document")
                    .setItems(options) { dialog, which ->
                        when (which) {
                            0 -> showRenameDialog(document)
                            1 -> deleteDocument(document)
                        }
                    }
                    .show()
            }

            private fun showRenameDialog(document: Document) {
                val editText = EditText(itemView.context)
                editText.setText(document.name)
                AlertDialog.Builder(itemView.context)
                    .setTitle("Rename Document")
                    .setView(editText)
                    .setPositiveButton("Rename") { dialog, which ->
                        val newName = editText.text.toString()
                        if (newName.isNotEmpty()) {
                            document.name = newName
                            dbHelper.updateDocumentName(document.id, newName)
                            notifyDataSetChanged()
                        } else {
                            Toast.makeText(itemView.context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            private fun deleteDocument(document: Document) {
                dbHelper.deleteDocument(document.id)
                documentsList.remove(document)
                notifyDataSetChanged()
            }
        }
    }
}