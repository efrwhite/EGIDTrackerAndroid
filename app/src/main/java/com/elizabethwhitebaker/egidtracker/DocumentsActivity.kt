package com.elizabethwhitebaker.egidtracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The selected child's documents: files and photos copied into the app's storage, listed in
 * DBHelper. They stay on this device. Tap one to open it; Edit renames or deletes it.
 */
class DocumentsActivity : AppCompatActivity() {

    private lateinit var documentUploadLauncher: ActivityResultLauncher<Intent>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var dbHelper: DBHelper
    private lateinit var childId: String
    private var tempPhotoUri: Uri? = null

    private val dateFormat = SimpleDateFormat(DateField.DISPLAY_PATTERN, Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_documents)
        setupPageHeader(R.string.documents)
        childId = requireSelectedChild() ?: return // needs a child; offers to add one

        dbHelper = DBHelper(this)
        // One-time move of documents saved by older versions, which weren't kept per child
        dbHelper.migrateLegacyDocuments(this, childId)

        CurrentChild.getName(this)?.takeIf { it.isNotBlank() }?.let {
            findViewById<TextView>(R.id.documentsLabel).text = getString(R.string.childs_documents, it)
        } ?: findViewById<TextView>(R.id.documentsLabel).setText(R.string.documents)

        documentUploadLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Backing out of the picker isn't an error, so nothing to say then
            result.data?.data?.takeIf { result.resultCode == RESULT_OK }?.let { addDocument(it) }
        }
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = tempPhotoUri ?: return@registerForActivityResult
            if (success) addDocument(uri, getString(R.string.photo_on, dateFormat.format(Date())))
            // addDocument keeps its own copy; drop the camera's temporary file either way
            Photos.deleteIfOwned(this, uri.toString())
            tempPhotoUri = null
        }
        // The app declares the camera permission, so the camera can't open until it's granted
        requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) takePhoto()
            else Toast.makeText(this, "Camera permission is needed to take a photo", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.addButton).setOnClickListener { showAddDocumentOptions() }
        showDocuments()
    }

    private fun showDocuments() {
        val rows = dbHelper.getAllDocuments(childId)
            .sortedByDescending { it.date }
            .map { doc ->
                ListRow(doc.name, describe(doc), getString(R.string.edit),
                    onClick = { openDocument(doc) },
                    onAction = { showEditOptions(doc) })
            }
        fillList(findViewById<LinearLayout>(R.id.documentRows), findViewById(R.id.documentsEmpty), rows)
    }

    /** e.g. "PDF · 120 kB · Added 11/24/2026" */
    private fun describe(doc: Document): String {
        val kind = when {
            doc.type == "application/pdf" -> "PDF"
            doc.type.startsWith("image/") -> "Image"
            else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(doc.type)?.uppercase() ?: "File"
        }
        return listOf(kind, Formatter.formatShortFileSize(this, doc.size),
            getString(R.string.document_added_on, dateFormat.format(doc.date))).joinToString(" · ")
    }

    /** --- Adding --- */

    private fun showAddDocumentOptions() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_document)
            .setItems(arrayOf(getString(R.string.take_photo), getString(R.string.choose_file))) { _, which ->
                when (which) {
                    0 -> if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) takePhoto()
                    else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    1 -> loadDocument()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadDocument() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        documentUploadLauncher.launch(intent)
    }

    private fun takePhoto() {
        val uri = Photos.newPhotoUri(this)
        tempPhotoUri = uri
        takePictureLauncher.launch(uri)
    }

    /** Copies [uri] into the app's storage and lists it, named [displayName] or the file's name. */
    private fun addDocument(uri: Uri, displayName: String? = null) {
        var name = displayName
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                if (name == null) name = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                size = it.getLong(it.getColumnIndexOrThrow(OpenableColumns.SIZE))
            }
        }
        val docName = name ?: "Document"
        val type = contentResolver.getType(uri) ?: "application/octet-stream"
        // Keep an extension on the stored file (camera photos are named without one)
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
        val fileName = if (ext != null && !docName.endsWith(".$ext", ignoreCase = true)) "$docName.$ext" else docName
        val savedFilePath = copyDocumentToAppStorage(uri, fileName)
        if (savedFilePath == null) {
            Toast.makeText(this, "Failed to save the document.", Toast.LENGTH_SHORT).show()
            return
        }
        if (size <= 0) size = File(savedFilePath).length()
        dbHelper.insertDocument(childId, docName, savedFilePath, type, size, Date().time, null)
        showDocuments()
    }

    private fun copyDocumentToAppStorage(uri: Uri, fileName: String): String? = runCatching {
        // Unique name, so two documents called e.g. "report.pdf" don't overwrite each other
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(filesDir, "doc_${System.currentTimeMillis()}_$safeName")
        contentResolver.openInputStream(uri)!!.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        file.absolutePath
    }.getOrNull()

    /** --- Open, rename, delete --- */

    private fun openDocument(document: Document) {
        val file = File(document.url)
        if (!file.exists()) {
            Toast.makeText(this, "This file is no longer on this phone.", Toast.LENGTH_SHORT).show()
            return
        }
        val fileUri = FileProvider.getUriForFile(this, "com.elizabethwhitebaker.egidtracker.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(fileUri, document.type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app on this phone can open this kind of file.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditOptions(document: Document) {
        MaterialAlertDialogBuilder(this)
            .setTitle(document.name)
            .setItems(arrayOf(getString(R.string.rename), getString(R.string.delete))) { _, which ->
                when (which) {
                    0 -> showRenameDialog(document)
                    1 -> confirmDelete(document)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRenameDialog(document: Document) {
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.document_name)
        }
        val input = TextInputEditText(layout.context).apply {
            setText(document.name)
            setSelection(document.name.length)
        }
        layout.addView(input)
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(layout)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_document)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.rename, null) // set below, so an empty name keeps it open
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val newName = input.text.toString().trim()
            if (newName.isEmpty()) {
                layout.error = getString(R.string.error_required)
                return@setOnClickListener
            }
            dbHelper.updateDocumentName(document.id, newName)
            dialog.dismiss()
            showDocuments()
        }
    }

    private fun confirmDelete(document: Document) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_document_title, document.name))
            .setMessage(R.string.delete_document_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                dbHelper.deleteDocument(this, document.id)
                showDocuments()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::dbHelper.isInitialized) dbHelper.close()
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
}
