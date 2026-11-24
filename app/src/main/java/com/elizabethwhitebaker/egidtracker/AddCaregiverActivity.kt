package com.elizabethwhitebaker.egidtracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Adds a caregiver, or edits one (extra: caregiverId). The account holder's own profile, made at
 * sign-up, also shows the login username and offers Delete Account; other caregivers can be
 * removed. Same design as the child profile (AddChildActivity).
 */
class AddCaregiverActivity : AppCompatActivity() {

    private lateinit var firstNameEditText: EditText
    private lateinit var lastNameEditText: EditText
    private lateinit var firstNameLayout: TextInputLayout
    private lateinit var lastNameLayout: TextInputLayout
    private lateinit var saveButton: Button
    private lateinit var photoView: ImageView
    private lateinit var photoLink: TextView
    private lateinit var deleteAccountButton: TextView
    private var imageUri: Uri? = null // a newly picked photo, saved with the rest of the form
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var pickImageFromGalleryLauncher: ActivityResultLauncher<String>
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>

    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var caregiverId: String? = null // Set in edit mode

    /**
     * True when editing the account holder's own caregiver profile (created at sign-up). Their
     * name is also stored on Users/{uid}, so it's kept in sync, and only this profile shows
     * Delete Account.
     */
    private var isAccountHolder = false
    private var photoUrl: String? = null // the saved photo, deleted from this device on removal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caregiver_profile)

        firstNameEditText = findViewById(R.id.caregiverFirstName)
        lastNameEditText = findViewById(R.id.caregiverLastName)
        firstNameLayout = findViewById(R.id.firstNameLayout)
        lastNameLayout = findViewById(R.id.lastNameLayout)
        saveButton = findViewById(R.id.saveButton)
        photoView = findViewById(R.id.caregiverPhoto)
        photoLink = findViewById(R.id.caregiverPhotoLink)
        deleteAccountButton = findViewById(R.id.deleteAccount)

        initLaunchers()

        // The account holder's profile deletes the whole account; other caregivers are just removed
        deleteAccountButton.setOnClickListener { if (isAccountHolder) deleteAccount() else confirmRemoveCaregiver() }

        caregiverId = intent.getStringExtra("caregiverId")
        caregiverId?.let {
            findViewById<TextView>(R.id.caregiverTitle).setText(R.string.edit_caregiver_title)
            populateCaregiverData(it)
        }

        photoView.setOnClickListener { showImagePickDialog() }
        photoLink.setOnClickListener { showImagePickDialog() }
        listOf(firstNameLayout, lastNameLayout).forEach { layout ->
            layout.editText?.doAfterTextChanged { layout.error = null }
        }
        saveButton.setOnClickListener { if (caregiverId == null) saveCaregiverInfo() else updateCaregiverInfo() }
    }

    private fun initLaunchers() {
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) showPhoto(imageUri) else imageUri = null
        }
        // The system picker needs no storage permission
        pickImageFromGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            val saved = Photos.copyToAppStorage(this, uri)
            if (saved == null) {
                Toast.makeText(this, "Couldn't use that photo", Toast.LENGTH_SHORT).show()
            } else {
                imageUri = saved
                showPhoto(saved)
            }
        }
        requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) takePhoto()
            else Toast.makeText(this, "Camera permission is needed to take a photo", Toast.LENGTH_SHORT).show()
        }
    }

    /** --- Photo --- */

    private fun showPhoto(uri: Any?) {
        if (uri == null) {
            Glide.with(this).clear(photoView)
            photoView.setImageDrawable(null) // plain salmon square
            photoLink.setText(R.string.add_photo)
        } else {
            Glide.with(this).load(uri).centerCrop().into(photoView)
            photoLink.setText(R.string.change_photo)
        }
    }

    private fun showImagePickDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Remove Photo")
        MaterialAlertDialogBuilder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) takePhoto()
                    else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    1 -> pickImageFromGalleryLauncher.launch("image/*")
                    2 -> removePhoto()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun takePhoto() {
        val uri = Photos.newPhotoUri(this)
        imageUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun removePhoto() {
        imageUri = null
        showPhoto(null)
        // In edit mode the stored photo is cleared right away
        caregiverId?.let { id ->
            Firebase.firestore.collection("Caregivers").document(id).update("imageUrl", "")
        }
    }

    /** --- Load and save --- */

    private fun populateCaregiverData(caregiverId: String) {
        Firebase.firestore.collection("Caregivers").document(caregiverId).get()
            .addOnSuccessListener { caregiverSnapshot ->
                if (!caregiverSnapshot.exists()) {
                    Toast.makeText(this, "Caregiver not found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                firstNameEditText.setText(caregiverSnapshot.getString("firstName") ?: "")
                lastNameEditText.setText(caregiverSnapshot.getString("lastName") ?: "")

                isAccountHolder = caregiverSnapshot.getBoolean("isAccountHolder") == true
                deleteAccountButton.setText(if (isAccountHolder) R.string.delete_account else R.string.remove_caregiver)
                deleteAccountButton.visibility = View.VISIBLE
                if (isAccountHolder) showUsername()

                photoUrl = caregiverSnapshot.getString("imageUrl")
                showPhoto(photoUrl?.takeIf { it.isNotEmpty() })
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching caregiver details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /** The login username, from the signed-in user's profile. */
    private fun showUsername() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        Firebase.firestore.collection("Users").document(uid).get()
            .addOnSuccessListener { doc ->
                val username = doc.getString("username").orEmpty()
                if (username.isNotBlank()) {
                    findViewById<EditText>(R.id.caregiverUsername).setText(username)
                    findViewById<View>(R.id.usernameGroup).visibility = View.VISIBLE
                }
            }
    }

    /** Marks empty name fields; returns the trimmed names when both are filled. */
    private fun validNames(): Pair<String, String>? {
        val firstName = firstNameEditText.text.toString().trim()
        val lastName = lastNameEditText.text.toString().trim()
        if (firstName.isEmpty()) firstNameLayout.error = getString(R.string.error_required)
        if (lastName.isEmpty()) lastNameLayout.error = getString(R.string.error_required)
        return if (firstName.isEmpty() || lastName.isEmpty()) null else firstName to lastName
    }

    private fun saveCaregiverInfo() {
        val (firstName, lastName) = validNames() ?: return

        val userMap = hashMapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "parentUserId" to firebaseAuth.currentUser?.uid,
            "imageUrl" to (imageUri?.toString() ?: "")
        )

        saveButton.isEnabled = false
        // A new document reference (rather than add()) so the write is applied locally right away
        awaitSave(Firebase.firestore.collection("Caregivers").document().set(userMap),
            onSaved = { queued ->
                toastSaved("Caregiver added", queued)
                goToNextActivity()
            },
            onFailed = { e ->
                saveButton.isEnabled = true
                Toast.makeText(this, "Failed to add caregiver: ${e.message}", Toast.LENGTH_SHORT).show()
            })
    }

    private fun updateCaregiverInfo() {
        val (firstName, lastName) = validNames() ?: return
        val uid = firebaseAuth.currentUser?.uid ?: return
        val id = caregiverId ?: return

        val userMap = hashMapOf<String, Any>(
            "firstName" to firstName,
            "lastName" to lastName,
            "parentUserId" to uid
        )
        // Only write the photo when one was picked, so editing the name keeps the old photo
        imageUri?.let { userMap["imageUrl"] = it.toString() }

        val db = Firebase.firestore
        val batch = db.batch()
            .set(db.collection("Caregivers").document(id), userMap, SetOptions.merge())
        if (isAccountHolder) {
            // Same person as the account: keep the profile's name in step
            batch.update(db.collection("Users").document(uid),
                mapOf("firstName" to firstName, "lastName" to lastName))
        }
        saveButton.isEnabled = false
        awaitSave(batch.commit(),
            onSaved = { queued ->
                // A replaced photo's old file is no longer used
                if (imageUri != null) Photos.deleteIfOwned(this, photoUrl)
                toastSaved("Caregiver updated", queued)
                goToNextActivity()
            },
            onFailed = { e ->
                saveButton.isEnabled = true
                Toast.makeText(this, "Failed to update caregiver: ${e.message}", Toast.LENGTH_SHORT).show()
            })
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

    /** Deletes all the user's data (see AccountDeletion), then the login itself. */
    private fun deleteAccountData(user: FirebaseUser) {
        AccountDeletion.deleteAllData(this, user.uid)
            .onSuccessTask {
                CurrentChild.clear(this) // needs the uid, so before delete()
                user.delete()
            }
            .addOnSuccessListener {
                Toast.makeText(this, "Account deleted.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
            .addOnFailureListener { e ->
                // The login still exists, so trying again picks up where this stopped
                Toast.makeText(this, "Couldn't delete the account: ${e.message}. Please try again.", Toast.LENGTH_LONG).show()
            }
    }

    private fun confirmRemoveCaregiver() {
        val id = caregiverId ?: return
        val name = firstNameEditText.text.toString().trim()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.remove_caregiver_title, name))
            .setMessage(getString(R.string.remove_caregiver_message, name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove_caregiver) { _, _ ->
                deleteAccountButton.isEnabled = false
                awaitSave(Firebase.firestore.collection("Caregivers").document(id).delete(),
                    onSaved = { queued ->
                        Photos.deleteIfOwned(this, photoUrl)
                        toastSaved(getString(R.string.caregiver_removed, name), queued)
                        goToNextActivity()
                    },
                    onFailed = { e ->
                        deleteAccountButton.isEnabled = true
                        Toast.makeText(this, "Failed to remove caregiver: ${e.message}", Toast.LENGTH_SHORT).show()
                    })
            }
            .show()
    }

    private fun goToNextActivity() {
        startActivity(Intent(this, ProfilesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
        finish()
    }
}
