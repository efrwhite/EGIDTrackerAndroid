package com.elizabethwhitebaker.egidtracker

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Date

class DocumentsActivity : AppCompatActivity() {

    private lateinit var addButton: Button
    private lateinit var docsRecyclerView: RecyclerView
    private lateinit var documentUploadLauncher: ActivityResultLauncher<Intent>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private var documentsList = mutableListOf<Document>()
    private lateinit var dbHelper: DBHelper
    private lateinit var childId: String
    private var tempPhotoUri: Uri? = null

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

        // Initialize the ActivityResultLauncher for loading a document
        documentUploadLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null && result.data!!.data != null) {
                processDocumentUri(result.data!!.data!!)
            } else {
                Toast.makeText(this, "No file selected or an error occurred.", Toast.LENGTH_SHORT).show()
            }
        }

        // Initialize the ActivityResultLauncher for taking a photo
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = tempPhotoUri  // Create a local copy to safely cast it
            if (success && uri != null) {
                processDocumentUri(uri)
            } else {
                Toast.makeText(this, "Failed to capture photo.", Toast.LENGTH_SHORT).show()
            }
        }


        // Show the dialog to choose between "Take Photo" and "Load Document"
        addButton.setOnClickListener {
            showAddDocumentOptions()
        }
    }

    // Function to show the dialog
    private fun showAddDocumentOptions() {
        val options = arrayOf("Take Photo", "Load Document")
        AlertDialog.Builder(this)
            .setTitle("Add Document")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> loadDocument()
                }
            }
            .show()
    }

    // Load document from the phone
    private fun loadDocument() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        documentUploadLauncher.launch(intent)
    }

    // Take a photo using the camera
    private fun takePhoto() {
        val photoFile = createImageFile()
        val uri = FileProvider.getUriForFile(
            this,
            "com.elizabethwhitebaker.egidtracker.fileprovider",
            photoFile
        )
        tempPhotoUri = uri  // Set tempPhotoUri after creating the URI
        takePictureLauncher.launch(uri)  // Use the local uri variable for launching
    }


    // Create a file to store the photo
    private fun createImageFile(): File {
        val fileName = "photo_${System.currentTimeMillis()}.jpg"
        // Use app-specific external storage to avoid Scoped Storage issues
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(storageDir, fileName)
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

                // Copy the file from the original location to the app's internal storage
                val savedFilePath = copyDocumentToAppStorage(uri, name)

                if (savedFilePath != null) {
                    // Save the document info to the database
                    val document = Document(0, name, savedFilePath, type, size, date, null)
                    dbHelper.insertDocument(childId, name, savedFilePath, type, size, date.time, null)
                    documentsList.add(document)
                    docsRecyclerView.adapter?.notifyDataSetChanged()
                } else {
                    Toast.makeText(this, "Failed to save the document.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Helper function to copy the document to internal storage
    private fun copyDocumentToAppStorage(uri: Uri, fileName: String): String? {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val file = File(filesDir, fileName)  // Save to internal storage
            val outputStream = FileOutputStream(file)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            return file.absolutePath // Return the file path
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // DBHelper and DocumentAdapter remain the same
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

        // In your DBHelper, ensure you're storing the file path
        fun insertDocument(childId: String, name: String, filePath: String, type: String, size: Long, date: Long, thumbnail: String?) {
            val db = this.writableDatabase
            val contentValues = ContentValues().apply {
                put(COLUMN_NAME, name)
                put(COLUMN_URL, filePath)  // Save the file path instead of the URI
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
                // Set click listener for the entire row
                itemView.setOnClickListener {
                    val document = documents[adapterPosition]
                    openDocument(document)
                }

                // Set click listener for the nameTextView (optional, if you want individual clicks)
                nameTextView.setOnClickListener {
                    val document = documents[adapterPosition]
                    openDocument(document)
                }

                // Set click listener for the edit button
                editButton.setOnClickListener {
                    val document = documents[adapterPosition]
                    showEditOptionsDialog(document)
                }
            }

            fun bind(document: Document) {
                nameTextView.text = document.name
                editButton.visibility = View.VISIBLE
            }

            private fun openDocument(document: Document) {
                val file = File(document.url)
                val fileUri: Uri = FileProvider.getUriForFile(
                    this@DocumentsActivity,
                    "com.elizabethwhitebaker.egidtracker.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW)
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
