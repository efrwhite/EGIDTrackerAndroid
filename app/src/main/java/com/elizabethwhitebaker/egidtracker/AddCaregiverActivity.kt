package com.elizabethwhitebaker.egidtracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddCaregiverActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var firstNameEditText: EditText
    private lateinit var lastNameEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var imageView: ImageView
    private lateinit var pictureButton: Button
    private lateinit var deleteAccountButton: Button
    private var imageUri: Uri? = null
    private var currentPhotoPath: String = ""
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var pickImageFromGalleryLauncher: ActivityResultLauncher<String>

    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var caregiverId: String? = null
    private var mode: String = "add" // Mode can be 'add' or 'edit'

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caregiver_profile)

        usernameEditText = findViewById(R.id.editText2)
        firstNameEditText = findViewById(R.id.editText)
        lastNameEditText = findViewById(R.id.editText3)
        saveButton = findViewById(R.id.saveButton)
        imageView = findViewById(R.id.imageView)
        pictureButton = findViewById(R.id.button)
        deleteAccountButton = findViewById(R.id.deleteAccount)

        imageView.setImageResource(R.drawable.default_profile_picture)  // Set default image initially

        deleteAccountButton.setOnClickListener {
            deleteAccount()
        }

        initLaunchers()

        // Check if this is the first-time user and get the username from the intent
        caregiverId = intent.getStringExtra("caregiverId")
        val username = intent.getStringExtra("username")

        if (username != null) {
            usernameEditText.setText(username)  // Set the username in the EditText
            usernameEditText.isEnabled = false  // Disable editing of the username
        }

        if (caregiverId != null) {
            mode = "edit"
            populateCaregiverData(caregiverId!!)
        }

        pictureButton.setOnClickListener {
            showImagePickDialog()
        }

        saveButton.setOnClickListener {
            if (mode == "add") {
                saveCaregiverInfo()
            } else {
                updateCaregiverInfo()
            }
        }
    }


    private fun initLaunchers() {
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {
                imageView.setImageURI(imageUri)
                saveImageInfoToDatabase()
            } else {
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        }

        pickImageFromGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                imageUri = it
                imageView.setImageURI(it)
                saveImageInfoToDatabase()
            }
        }
    }

    private fun showImagePickDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Delete Picture", "Cancel")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> {
                    // Take photo with camera
                    checkAndRequestCameraPermission()
                }
                1 -> {
                    // Pick image from gallery
                    pickImageFromGallery()
                }
                2 -> {
                    // Delete profile picture
                    deleteProfilePicture()
                }
                3 -> dialog.dismiss()
            }
        }
        builder.show()
    }

    private fun deleteProfilePicture() {
        imageView.setImageResource(R.drawable.default_profile_picture)  // Set the default image
        imageUri = null  // Clear the image URI

        // Update Firestore to remove the image URL
        caregiverId?.let { id ->
            Firebase.firestore.collection("Caregivers").document(id)
                .update("imageUrl", "")  // Clear the imageUrl field in Firestore
        }
    }


    private fun checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        } else {
            dispatchTakePictureIntent()
        }
    }

    private fun dispatchTakePictureIntent() {
        val photoFile = createImageFile()
        val uri = FileProvider.getUriForFile(this, "com.elizabethwhitebaker.egidtracker.fileprovider", photoFile)
        imageUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun pickImageFromGallery() {
        pickImageFromGalleryLauncher.launch("image/*")
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun saveImageInfoToDatabase() {
        val imageUrl = imageUri?.toString() ?: ""

        caregiverId?.let { id ->
            Firebase.firestore.collection("Caregivers").document(id)
                .update("imageUrl", imageUrl)
        }
    }

    private fun populateCaregiverData(caregiverId: String) {
        Firebase.firestore.collection("Caregivers").document(caregiverId).get()
            .addOnSuccessListener { caregiverSnapshot ->
                if (caregiverSnapshot.exists()) {
                    firstNameEditText.setText(caregiverSnapshot.getString("firstName") ?: "")
                    lastNameEditText.setText(caregiverSnapshot.getString("lastName") ?: "")

                    // Fetch and populate the username
                    val parentUserId = caregiverSnapshot.getString("parentUserId") ?: ""
                    fetchUsername(parentUserId)

                    // Load the profile picture
                    val imageUrl = caregiverSnapshot.getString("imageUrl") ?: ""
                    if (imageUrl.isNotEmpty()) {
                        Glide.with(this).load(imageUrl).into(imageView)
                    } else {
                        imageView.setImageResource(R.drawable.default_profile_picture)
                    }
                } else {
                    Toast.makeText(this, "Caregiver not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching caregiver details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }



    private fun saveCaregiverInfo() {
        val firstName = firstNameEditText.text.toString().trim()
        val lastName = lastNameEditText.text.toString().trim()
        val imageUrl = imageUri?.toString() ?: ""

        if (firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
            return
        }

        val userMap = hashMapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "parentUserId" to firebaseAuth.currentUser?.uid,
            "imageUrl" to imageUrl
        )

        Firebase.firestore.collection("Caregivers").add(userMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Caregiver added successfully", Toast.LENGTH_SHORT).show()
                goToNextActivity()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to add caregiver: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateCaregiverInfo() {
        val firstName = firstNameEditText.text.toString().trim()
        val lastName = lastNameEditText.text.toString().trim()
        val imageUrl = imageUri?.toString() ?: ""

        if (firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
            return
        }

        caregiverId?.let { id ->
            val userMap = hashMapOf(
                "firstName" to firstName,
                "lastName" to lastName,
                "parentUserId" to firebaseAuth.currentUser?.uid,
                "imageUrl" to imageUrl
            )

            Firebase.firestore.collection("Caregivers").document(id).set(userMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Caregiver updated successfully", Toast.LENGTH_SHORT).show()
                    goToNextActivity()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update caregiver: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: Toast.makeText(this, "Error: Caregiver ID is missing.", Toast.LENGTH_SHORT).show()
    }

    private fun fetchUsername(parentUserId: String) {
        // Since document IDs are usernames and contain a 'userId' field for matching,
        // the query should fetch based on 'userId' being equal to parentUserId.
        Firebase.firestore.collection("Usernames")
            .whereEqualTo("userId", parentUserId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.documents.isNotEmpty()) {
                    // Assuming the document ID (username) is what we need to display
                    val username = documents.documents.first().id
                    usernameEditText.setText(username)
                    usernameEditText.isEnabled = false // Make the EditText uneditable
                } else {
                    Toast.makeText(this, "Username not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching username: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteAccount() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Delete Account")
        builder.setMessage("Are you sure you want to delete your account? If you are the only caregiver, this will delete all your child profiles as well.")
        builder.setPositiveButton("Delete") { dialog, _ ->
            dialog.dismiss()

            val currentUser = firebaseAuth.currentUser
            val uid = currentUser?.uid ?: return@setPositiveButton
            val db = Firebase.firestore
            val batch = db.batch()

            // Step 1: Delete Children
            db.collection("Children")
                .whereEqualTo("parentUserId", uid)
                .get()
                .addOnSuccessListener { childrenSnapshot ->
                    for (child in childrenSnapshot.documents) {
                        batch.delete(child.reference)
                    }

                    // Step 2: Delete Caregivers
                    db.collection("Caregivers")
                        .whereEqualTo("parentUserId", uid)
                        .get()
                        .addOnSuccessListener { caregiverSnapshot ->
                            for (caregiver in caregiverSnapshot.documents) {
                                batch.delete(caregiver.reference)
                            }

                            // Step 3: Delete Usernames
                            db.collection("Usernames")
                                .whereEqualTo("userId", uid)
                                .get()
                                .addOnSuccessListener { usernamesSnapshot ->
                                    for (usernameDoc in usernamesSnapshot.documents) {
                                        batch.delete(usernameDoc.reference)
                                    }

                                    // Step 4: Delete from Users collection (doc ID == UID)
                                    db.collection("Users").document(uid).get()
                                        .addOnSuccessListener { userDoc ->
                                            if (userDoc.exists()) {
                                                batch.delete(userDoc.reference)
                                            }

                                            // Step 5: Commit batch
                                            batch.commit()
                                                .addOnSuccessListener {
                                                    // Step 6: Delete Authentication user
                                                    currentUser.delete()
                                                        .addOnSuccessListener {
                                                            Toast.makeText(this, "Account deleted successfully.", Toast.LENGTH_LONG).show()
                                                            startActivity(Intent(this, MainActivity::class.java).apply {
                                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                            })
                                                        }
                                                        .addOnFailureListener {
                                                            Toast.makeText(this, "Failed to delete auth: ${it.message}", Toast.LENGTH_LONG).show()
                                                        }
                                                }
                                                .addOnFailureListener {
                                                    Toast.makeText(this, "Batch delete failed: ${it.message}", Toast.LENGTH_LONG).show()
                                                }
                                        }
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this, "Failed to delete username: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to delete caregiver: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to delete children: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }



    private fun goToNextActivity() {
        val nextActivityIntent = when {
            intent.getBooleanExtra("isFirstTimeUser", false) && mode == "add" -> {
                Intent(this, AddChildActivity::class.java).apply {
                    putExtra("isFirstTimeUser", true)
                }
            }
            else -> {
                Intent(this, ProfilesActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        }
        startActivity(nextActivityIntent)
        finish()
    }
}
