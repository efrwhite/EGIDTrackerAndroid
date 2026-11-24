package com.elizabethwhitebaker.egidtracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AddCaregiverActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var firstNameEditText: EditText
    private lateinit var lastNameEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var imageView: ImageView
    private lateinit var pictureButton: Button
    private lateinit var deleteAccountButton: Button
    private var imageUri: Uri? = null
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var pickImageFromGalleryLauncher: ActivityResultLauncher<String>

    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var caregiverId: String? = null
    private var mode: String = "add" // 'add' or 'edit'

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

        imageView.setImageResource(R.drawable.default_profile_picture)

        deleteAccountButton.setOnClickListener { deleteAccount() }
        initLaunchers()

        caregiverId = intent.getStringExtra("caregiverId")
        val usernameFromIntent = intent.getStringExtra("username")

        if (usernameFromIntent != null) {
            usernameEditText.setText(usernameFromIntent)
            usernameEditText.isEnabled = false
        } else {
            // First time open without username extra? Fetch from Users using current uid
            fetchUsernameFromUsers(firebaseAuth.currentUser?.uid)
        }

        if (caregiverId != null) {
            mode = "edit"
            populateCaregiverData(caregiverId!!)
        }

        pictureButton.setOnClickListener { showImagePickDialog() }

        saveButton.setOnClickListener {
            if (mode == "add") saveCaregiverInfo() else updateCaregiverInfo()
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
            uri ?: return@registerForActivityResult
            val saved = Photos.copyToAppStorage(this, uri)
            if (saved == null) {
                Toast.makeText(this, "Couldn't use that photo", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            imageUri = saved
            imageView.setImageURI(saved)
            saveImageInfoToDatabase()
        }
    }

    private fun showImagePickDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Delete Picture", "Cancel")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> checkAndRequestCameraPermission()
                1 -> pickImageFromGallery()
                2 -> deleteProfilePicture()
                3 -> dialog.dismiss()
            }
        }
        builder.show()
    }

    private fun deleteProfilePicture() {
        imageView.setImageResource(R.drawable.default_profile_picture)
        imageUri = null

        caregiverId?.let { id ->
            Firebase.firestore.collection("Caregivers").document(id)
                .update("imageUrl", "")
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
        val uri = Photos.newPhotoUri(this)
        imageUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun pickImageFromGallery() {
        pickImageFromGalleryLauncher.launch("image/*")
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
                if (!caregiverSnapshot.exists()) {
                    Toast.makeText(this, "Caregiver not found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                firstNameEditText.setText(caregiverSnapshot.getString("firstName") ?: "")
                lastNameEditText.setText(caregiverSnapshot.getString("lastName") ?: "")

                val parentUserId = caregiverSnapshot.getString("parentUserId") ?: ""
                fetchUsernameFromUsers(parentUserId)

                val imageUrl = caregiverSnapshot.getString("imageUrl") ?: ""
                if (imageUrl.isNotEmpty()) {
                    Glide.with(this).load(imageUrl).into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.default_profile_picture)
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

    private fun fetchUsernameFromUsers(parentUserId: String?) {
        val uid = parentUserId ?: return
        Firebase.firestore.collection("Users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val username = doc.getString("username") ?: ""
                    if (username.isNotBlank()) {
                        usernameEditText.setText(username)
                        usernameEditText.isEnabled = false
                    }
                } else {
                    Toast.makeText(this, "User not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching username: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Deletes the account after the user re-enters their password. Firebase only deletes a login
     * that signed in recently, so re-authenticating first means the final step can't fail after
     * the data is already gone (which used to leave a login with no profile or username).
     */
    private fun deleteAccount() {
        val user = firebaseAuth.currentUser ?: return
        val email = user.email ?: return

        val passwordLayout = TextInputLayout(this, null,
            com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            hint = getString(R.string.password)
        }
        val passwordInput = TextInputEditText(passwordLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        passwordLayout.addView(passwordInput)
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(passwordLayout)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_account_title)
            .setMessage(R.string.delete_account_message)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete, null) // set below, so a wrong password keeps it open
            .show()

        val deleteButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        deleteButton.setOnClickListener {
            val password = passwordInput.text.toString()
            if (password.isEmpty()) {
                passwordLayout.error = getString(R.string.error_required)
                return@setOnClickListener
            }
            deleteButton.isEnabled = false
            user.reauthenticate(EmailAuthProvider.getCredential(email, password))
                .addOnSuccessListener {
                    dialog.dismiss()
                    deleteAccountData(user)
                }
                .addOnFailureListener { e ->
                    deleteButton.isEnabled = true
                    passwordLayout.error = getString(
                        if (e is FirebaseAuthInvalidCredentialsException) R.string.error_wrong_password
                        else R.string.error_network
                    )
                }
        }
    }

    /** Deletes the user's children, caregivers, profile and username, then the login itself. */
    private fun deleteAccountData(user: FirebaseUser) {
        val uid = user.uid
        val db = Firebase.firestore
        val batch = db.batch()
        val onFailure = { e: Exception ->
            Toast.makeText(this, "Couldn't delete the account: ${e.message}", Toast.LENGTH_LONG).show()
        }

        db.collection("Children").whereEqualTo("parentUserId", uid).get()
            .addOnSuccessListener { children ->
                children.documents.forEach { batch.delete(it.reference) }

                db.collection("Caregivers").whereEqualTo("parentUserId", uid).get()
                    .addOnSuccessListener { caregivers ->
                        caregivers.documents.forEach { batch.delete(it.reference) }

                        // The profile (doc ID == UID), and the username so someone else can use it
                        db.collection("Users").document(uid).get()
                            .addOnSuccessListener { userDoc ->
                                if (userDoc.exists()) {
                                    batch.delete(userDoc.reference)
                                    userDoc.getString("username")
                                        ?.takeIf { Usernames.isValid(it) }
                                        ?.let { batch.delete(Usernames.ref(it)) }
                                }

                                batch.commit()
                                    .addOnSuccessListener {
                                        CurrentChild.clear(this) // needs the uid, so before delete()
                                        user.delete()
                                            .addOnSuccessListener {
                                                Toast.makeText(this, "Account deleted.", Toast.LENGTH_LONG).show()
                                                startActivity(Intent(this, MainActivity::class.java).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                })
                                            }
                                            .addOnFailureListener(onFailure)
                                    }
                                    .addOnFailureListener(onFailure)
                            }
                            .addOnFailureListener(onFailure)
                    }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    private fun goToNextActivity() {
        startActivity(Intent(this, ProfilesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
        finish()
    }
}
