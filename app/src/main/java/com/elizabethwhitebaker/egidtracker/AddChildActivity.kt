package com.elizabethwhitebaker.egidtracker

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddChildActivity : AppCompatActivity() {
    private lateinit var firstNameEditText: EditText
    private lateinit var lastNameEditText: EditText
    private lateinit var birthDateEditText: EditText
    private lateinit var genderInput: AutoCompleteTextView
    private lateinit var dietInput: AutoCompleteTextView
    private lateinit var saveButton: Button
    private lateinit var pictureButton: Button
    private lateinit var imageView: ImageView
    private var imageUri: Uri? = null
    private var currentPhotoPath: String = ""
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var pickImageFromGalleryLauncher: ActivityResultLauncher<String>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var childId: String? = null // Used for edit mode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_profile)

        firstNameEditText = findViewById(R.id.editText2)
        lastNameEditText = findViewById(R.id.editText)
        birthDateEditText = findViewById(R.id.editTextDate)
        genderInput = findViewById(R.id.GenderInputFields)
        dietInput = findViewById(R.id.DietInputFields)
        saveButton = findViewById(R.id.saveButton)
        pictureButton = findViewById(R.id.button)
        imageView = findViewById(R.id.imageView)

        initLaunchers()

        // Check if in edit mode and if so, fetch and populate child data
        childId = intent.getStringExtra("childId")
        if (childId != null) {
            fetchAndPopulateChildData(childId!!)
        }

        pictureButton.setOnClickListener {
            showImagePickDialog()
        }

        saveButton.setOnClickListener {
            if (childId == null) {
                saveChildInfo()
            } else {
                updateChildInfo()
            }
        }

        setupDropdownMenus()

        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    // Call the method that was initially intended
                    dispatchTakePictureIntent()
                } else {
                    Toast.makeText(this, "Permission is needed to proceed", Toast.LENGTH_SHORT)
                        .show()
                }
            }
    }

    override fun onResume() {
        super.onResume()
        setupDropdownMenus()
    }

    private fun initLaunchers() {
        takePictureLauncher =
            registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
                if (isSuccess) {
                    imageView.setImageURI(imageUri)
                    saveImageInfoToDatabase()
                }
            }

        pickImageFromGalleryLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    imageView.setImageURI(uri)
                    imageUri = uri
                    saveImageInfoToDatabase()
                }
            }
    }

    private fun fetchAndPopulateChildData(childId: String) {
        Firebase.firestore.collection("Children").document(childId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    firstNameEditText.setText(document.getString("firstName"))
                    lastNameEditText.setText(document.getString("lastName"))
                    birthDateEditText.setText(document.getString("birthDate"))
                    genderInput.setText(document.getString("gender"))
                    dietInput.setText(document.getString("diet"))
                } else {
                    Toast.makeText(this, "Child not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load child data: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun saveChildInfo() {
        val userMap = hashMapOf(
            "firstName" to firstNameEditText.text.toString().trim(),
            "lastName" to lastNameEditText.text.toString().trim(),
            "birthDate" to birthDateEditText.text.toString().trim(),
            "gender" to genderInput.text.toString().trim(),
            "diet" to dietInput.text.toString().trim(),
            "parentUserId" to firebaseAuth.currentUser?.uid
        )

        Firebase.firestore.collection("Children").add(userMap)
            .addOnSuccessListener { documentReference ->
                val newChildId = documentReference.id
                saveCurrentChildId(newChildId)
                Toast.makeText(this, "Child added successfully", Toast.LENGTH_SHORT).show()
                goToNextActivity()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to add child: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateChildInfo() {
        val userMap = hashMapOf(
            "firstName" to firstNameEditText.text.toString().trim(),
            "lastName" to lastNameEditText.text.toString().trim(),
            "birthDate" to birthDateEditText.text.toString().trim(),
            "gender" to genderInput.text.toString().trim(),
            "diet" to dietInput.text.toString().trim(),
            "parentUserId" to firebaseAuth.currentUser?.uid
        )

        childId?.let { id ->
            Firebase.firestore.collection("Children").document(id).set(userMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Child updated successfully", Toast.LENGTH_SHORT).show()
                    goToNextActivity()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update child: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
        }
    }

    private fun saveCurrentChildId(childId: String) {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("CurrentChildId", childId)
            apply()
        }
    }

    private fun setupDropdownMenus() {
        val genderOptions = resources.getStringArray(R.array.gender_options)
        val genderAdapter =
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genderOptions)
        genderInput.setAdapter(genderAdapter)

        val dietOptions = resources.getStringArray(R.array.diet_options)
        val dietAdapter =
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, dietOptions)
        dietInput.setAdapter(dietAdapter)
    }

    private fun goToNextActivity() {
        val isFirstTimeUser = intent.getBooleanExtra("isFirstTimeUser", false)
        val nextActivityIntent = if (isFirstTimeUser) {
            Intent(this, HomeActivity::class.java).apply {
                // Clear the activity stack to prevent backtracking
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        } else {
            Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
        startActivity(nextActivityIntent)
    }

    private fun showImagePickDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> {
                    // Check and request camera permission
                    when {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            // Permission is already granted
                            dispatchTakePictureIntent()
                        }

                        else -> {
                            // Request camera permission
                            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }

                1 -> {
                    // Check and request read media image permission
                    when {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_MEDIA_IMAGES
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            // Permission is already granted
                            pickImageFromGallery()
                        }

                        else -> {
                            // Request read media image permission
                            requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                        }
                    }
                }

                2 -> {
                    if (dialog is DialogInterface) {
                        dialog.dismiss() // Correctly dismiss the dialog
                    }
                }
            }
        }

        val dialog = builder.create() // Create the AlertDialog from the builder
        dialog.show() // Show the dialog
    }


    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Toast.makeText(this, "Photo file creation failed", Toast.LENGTH_SHORT).show()
                    null
                }
                photoFile?.also {
                    val uri = FileProvider.getUriForFile(this, "com.example.fire.fileprovider", it)
                    imageUri = uri
                    takePictureLauncher.launch(uri)
                }
            }
        }
    }


    private fun pickImageFromGallery() {
        pickImageFromGalleryLauncher.launch("image/*")
    }

    private fun createImageFile(): File {
        val timeStamp: String =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun saveImageInfoToDatabase() {
        val imageName = imageUri?.lastPathSegment
        val imageUrl = imageUri.toString()
        val date = System.currentTimeMillis()

        val userMap = hashMapOf(
            "imageUri" to imageUrl,
            "imageName" to imageName,
            "date" to date
        )

        childId?.let { id ->
            Firebase.firestore.collection("Children").document(id)
                .collection("Images").add(userMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Image saved successfully", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
        }
    }

    private fun checkPermissionsAndTakePicture() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        } else {
            dispatchTakePictureIntent()
        }
    }

    private fun checkPermissionsAndPickImage() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_STORAGE_PERMISSION
            )
        } else {
            pickImageFromGallery()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    dispatchTakePictureIntent()
                } else {
                    Toast.makeText(
                        this,
                        "Camera permission is required to take pictures",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pickImageFromGallery()
                } else {
                    Toast.makeText(
                        this,
                        "Storage permission is required to pick images",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    companion object {
        const val REQUEST_CAMERA_PERMISSION = 1
        const val REQUEST_STORAGE_PERMISSION = 2
    }
}

