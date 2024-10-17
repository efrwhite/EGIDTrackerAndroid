package com.elizabethwhitebaker.egidtracker

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddChildActivity : AppCompatActivity() {
    private lateinit var firstNameEditText: EditText
    private lateinit var lastNameEditText: EditText
    private lateinit var birthDateInput: EditText
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
        birthDateInput = findViewById(R.id.editTextDate)
        genderInput = findViewById(R.id.GenderInputFields)
        dietInput = findViewById(R.id.DietInputFields)
        saveButton = findViewById(R.id.saveButton)
        pictureButton = findViewById(R.id.button)
        imageView = findViewById(R.id.imageView)

        imageView.setImageResource(R.drawable.default_profile_picture)  // Set default image initially

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

        birthDateInput.setOnClickListener {
            showDatePickerDialog()
        }

        dietInput.setOnClickListener {
            if (childId != null) {
                // Show the pop-up only in edit mode
                val builder = android.app.AlertDialog.Builder(this)
                builder.setTitle("Reminder")
                builder.setMessage("You should have indicated which cleared allergen facilitated this diet change in the allergen notes field.")

                // Add the OK button
                builder.setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss() // Dismiss the dialog when OK is clicked
                    // Proceed with further interaction (e.g., showing diet options)
                    dietInput.showDropDown() // Show the dropdown for diet selection
                }

                // Create and show the dialog
                val alertDialog = builder.create()
                alertDialog.show()
            } else {
                // If not in edit mode, just show the dropdown
                dietInput.showDropDown()
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
                    // Ensure the image updates immediately in the ImageView after taking a picture
                    imageView.setImageURI(imageUri)  // Immediately show the selected image
                    saveImageInfoToDatabase()  // Save the image info right after it's selected
                } else {
                    Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
                }
            }

        pickImageFromGalleryLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    imageUri = it
                    imageView.setImageURI(it)  // Immediately show the selected image
                    saveImageInfoToDatabase()  // Save the image info right after it's selected
                }
            }
    }



    private fun fetchAndPopulateChildData(childId: String) {
        Firebase.firestore.collection("Children").document(childId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Populate child info fields
                    firstNameEditText.setText(document.getString("firstName"))
                    lastNameEditText.setText(document.getString("lastName"))
                    birthDateInput.setText(document.getString("birthDate"))
                    genderInput.setText(document.getString("gender"))
                    dietInput.setText(document.getString("diet"))

                    // Load existing profile picture if available
                    val imageUrl = document.getString("imageUrl") ?: ""
                    if (imageUrl.isNotEmpty()) {
                        Glide.with(this).load(imageUrl).into(imageView) // Display profile picture
                    } else {
                        imageView.setImageResource(R.drawable.default_profile_picture) // Set default placeholder
                    }

                    setupDropdownMenus()
                } else {
                    Toast.makeText(this, "Child not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load child data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun saveChildInfo() {
        val userMap = hashMapOf(
            "firstName" to firstNameEditText.text.toString().trim(),
            "lastName" to lastNameEditText.text.toString().trim(),
            "birthDate" to birthDateInput.text.toString().trim(),
            "gender" to genderInput.text.toString().trim(),
            "diet" to dietInput.text.toString().trim(),
            "parentUserId" to firebaseAuth.currentUser?.uid,
            "imageUrl" to (imageUri?.toString() ?: "")  // Save imageUri if it's not null, else empty string
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
            "birthDate" to birthDateInput.text.toString().trim(),
            "gender" to genderInput.text.toString().trim(),
            "diet" to dietInput.text.toString().trim(),
            "parentUserId" to firebaseAuth.currentUser?.uid,
            "imageUrl" to (imageUri?.toString() ?: "")  // Save imageUri if it's not null, else empty string
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
        val options = arrayOf("Take Photo", "Choose from Gallery", "Delete Picture", "Cancel")
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
                    // Choose from gallery
                    when {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_MEDIA_IMAGES
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            pickImageFromGallery()
                        }

                        else -> {
                            // Request permission
                            requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                        }
                    }
                }

                2 -> {
                    // Delete profile picture
                    deleteProfilePicture()
                }

                3 -> dialog.dismiss()  // Cancel
            }
        }
        builder.show()
    }

    private fun deleteProfilePicture() {
        imageView.setImageResource(R.drawable.default_profile_picture) // Set a default image locally
        imageUri = null  // Clear the URI

        // Update Firestore to remove the image URL
        childId?.let { id ->
            Firebase.firestore.collection("Children").document(id)
                .update("imageUrl", "")  // Clear imageUrl in Firestore
        }
    }


    private fun saveImageInfoToDatabase() {
        val imageUrl = imageUri?.toString() ?: ""  // Get the image URL or empty if none

        // Store the image URL in Firestore
        childId?.let { id ->
            Firebase.firestore.collection("Children").document(id)
                .update("imageUrl", imageUrl)

        }
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
                    val uri = FileProvider.getUriForFile(this, "com.elizabethwhitebaker.egidtracker.fileprovider", it)
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

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
                // Format the date and set it to the EditText
                val formattedDate = "${selectedMonth + 1}/$selectedDay/$selectedYear"
                birthDateInput.setText(formattedDate)
            },
            year, month, day
        )

        datePickerDialog.show()
    }

    companion object {
        const val REQUEST_CAMERA_PERMISSION = 1
        const val REQUEST_STORAGE_PERMISSION = 2
    }
}

