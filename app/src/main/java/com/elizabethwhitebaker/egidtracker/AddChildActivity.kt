package com.elizabethwhitebaker.egidtracker

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Adds a child, or edits one (pass [EXTRA_CHILD_ID]). It's also the last step of sign-up
 * ([EXTRA_FIRST_TIME_USER]), where it can be skipped: the child can be added later from Profiles.
 */
class AddChildActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHILD_ID = "childId"
        const val EXTRA_FIRST_TIME_USER = "isFirstTimeUser"
    }

    private lateinit var firstNameEditText: EditText
    private lateinit var lastNameEditText: EditText
    private lateinit var birthDateInput: EditText
    private lateinit var genderInput: AutoCompleteTextView
    private lateinit var dietInput: AutoCompleteTextView
    private lateinit var firstNameLayout: TextInputLayout
    private lateinit var lastNameLayout: TextInputLayout
    private lateinit var birthDateLayout: TextInputLayout
    private lateinit var saveButton: Button
    private lateinit var photoView: ImageView
    private lateinit var photoLink: TextView

    private var imageUri: Uri? = null
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var pickImageFromGalleryLauncher: ActivityResultLauncher<String>
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>

    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var childId: String? = null // Set in edit mode
    private var savedDiet: String? = null // The child's diet before editing
    private var dietReminderShown = false
    private val isFirstTimeUser by lazy { intent.getBooleanExtra(EXTRA_FIRST_TIME_USER, false) }

    // Stored format, unchanged from earlier versions so existing birth dates still read correctly
    private val birthDateFormat = SimpleDateFormat("M/d/yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_profile)

        firstNameEditText = findViewById(R.id.childFirstName)
        lastNameEditText = findViewById(R.id.childLastName)
        birthDateInput = findViewById(R.id.childBirthDate)
        genderInput = findViewById(R.id.childGender)
        dietInput = findViewById(R.id.childDiet)
        firstNameLayout = findViewById(R.id.firstNameLayout)
        lastNameLayout = findViewById(R.id.lastNameLayout)
        birthDateLayout = findViewById(R.id.birthDateLayout)
        saveButton = findViewById(R.id.saveButton)
        photoView = findViewById(R.id.childPhoto)
        photoLink = findViewById(R.id.childPhotoLink)

        initLaunchers()
        setupDropdowns()

        childId = intent.getStringExtra(EXTRA_CHILD_ID)
        val title = findViewById<TextView>(R.id.childTitle)
        when {
            childId != null -> {
                title.setText(R.string.edit_child)
                fetchAndPopulateChildData(childId!!)
                findViewById<View>(R.id.deleteChildLink).apply {
                    visibility = View.VISIBLE
                    setOnClickListener { confirmDeleteChild(childId!!) }
                }
            }
            isFirstTimeUser -> {
                // Sign-up: explain the step, offer Skip, and make Back skip too (Register is done)
                saveButton.setText(R.string.continue_label)
                findViewById<View>(R.id.childSubtitle).visibility = View.VISIBLE
                findViewById<View>(R.id.skipLink).apply {
                    visibility = View.VISIBLE
                    setOnClickListener { goHome() }
                }
                findViewById<View>(R.id.backButton).visibility = View.GONE
                onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() = goHome()
                })
            }
        }

        photoView.setOnClickListener { showImagePickDialog() }
        photoLink.setOnClickListener { showImagePickDialog() }

        birthDateInput.setOnClickListener { showDatePickerDialog() }
        birthDateLayout.setEndIconOnClickListener { showDatePickerDialog() }

        listOf(firstNameLayout, lastNameLayout, birthDateLayout).forEach { layout ->
            layout.editText?.doAfterTextChanged { layout.error = null }
        }

        saveButton.setOnClickListener { saveChild() }
    }

    private fun setupDropdowns() {
        val genders = resources.getStringArray(R.array.gender_options)
        genderInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, genders))

        val diets = resources.getStringArray(R.array.diet_options)
        dietInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, diets))
        // New children start on no diet; false = don't filter the list to the current text
        if (dietInput.text.isNullOrBlank()) dietInput.setText(diets.first(), false)

        // Changing an existing child's diet should come with an allergen note: remind once,
        // right after a different diet is picked
        dietInput.setOnItemClickListener { _, _, position, _ ->
            val changed = childId != null && savedDiet != null && diets[position] != savedDiet
            if (changed && !dietReminderShown) {
                dietReminderShown = true
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.reminder)
                    .setMessage(R.string.diet_change_reminder)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
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

    private fun fetchAndPopulateChildData(childId: String) {
        Firebase.firestore.collection("Children").document(childId).get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    Toast.makeText(this, "Child not found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                firstNameEditText.setText(document.getString("firstName"))
                lastNameEditText.setText(document.getString("lastName"))
                birthDateInput.setText(document.getString("birthDate"))
                genderInput.setText(document.getString("gender"), false)
                document.getString("diet")?.takeIf { it.isNotBlank() }?.let {
                    savedDiet = it
                    dietInput.setText(it, false)
                }
                showPhoto(document.getString("imageUrl")?.takeIf { it.isNotEmpty() })
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load child data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveChild() {
        val firstName = firstNameEditText.text.toString().trim()
        val lastName = lastNameEditText.text.toString().trim()
        val birthDate = birthDateInput.text.toString().trim()

        firstNameLayout.error = if (firstName.isEmpty()) getString(R.string.error_required) else null
        lastNameLayout.error = if (lastName.isEmpty()) getString(R.string.error_required) else null
        birthDateLayout.error = if (birthDate.isEmpty()) getString(R.string.error_required) else null
        if (firstName.isEmpty() || lastName.isEmpty() || birthDate.isEmpty()) return

        val childMap = hashMapOf<String, Any?>(
            "firstName" to firstName,
            "lastName" to lastName,
            "birthDate" to birthDate,
            "gender" to genderInput.text.toString().trim(),
            "diet" to dietInput.text.toString().trim(),
            "parentUserId" to firebaseAuth.currentUser?.uid
        )
        // Only write the photo when one was picked, so editing other fields keeps the old photo
        imageUri?.let { childMap["imageUrl"] = it.toString() }

        saveButton.isEnabled = false
        val isNew = childId == null
        if (isNew) childMap.putIfAbsent("imageUrl", "")
        // A new document reference (rather than add()) gives the id right away, also offline
        val ref = Firebase.firestore.collection("Children").let { children ->
            childId?.let { children.document(it) } ?: children.document()
        }
        awaitSave(ref.set(childMap, SetOptions.merge()),
            onSaved = { queued ->
                if (isNew) CurrentChild.set(this, ref.id, firstName)
                toastSaved(getString(if (isNew) R.string.child_saved else R.string.child_updated), queued)
                goToNextScreen()
            },
            onFailed = { onSaveFailed() })
    }

    private fun onSaveFailed() {
        saveButton.isEnabled = true
        Toast.makeText(this, R.string.error_save_child, Toast.LENGTH_LONG).show()
    }

    private fun confirmDeleteChild(id: String) {
        val name = firstNameEditText.text.toString().trim().ifEmpty { getString(R.string.this_child) }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_child_title, name))
            .setMessage(getString(R.string.delete_child_message, name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> deleteChild(id, name) }
            .show()
    }

    /** Needs a connection: see AccountDeletion.childRecordQueries. */
    private fun deleteChild(id: String, name: String) {
        saveButton.isEnabled = false
        Firebase.firestore.collection("Children").document(id).get(Source.SERVER)
            .onSuccessTask { child -> AccountDeletion.deleteChild(this, child) }
            .addOnSuccessListener {
                if (CurrentChild.getId(this) == id) CurrentChild.clear(this) // Home picks another
                Toast.makeText(this, getString(R.string.child_deleted, name), Toast.LENGTH_SHORT).show()
                goToNextScreen()
            }
            .addOnFailureListener {
                saveButton.isEnabled = true
                Toast.makeText(this, R.string.error_delete_child, Toast.LENGTH_LONG).show()
            }
    }

    /** Sign-up continues to Home; adding or editing from Profiles returns to Profiles. */
    private fun goToNextScreen() {
        if (isFirstTimeUser) {
            goHome()
        } else {
            // CLEAR_TOP recreates Profiles, so it reloads its lists (same as AddCaregiverActivity)
            startActivity(Intent(this, ProfilesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        }
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
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
        childId?.let { id ->
            Firebase.firestore.collection("Children").document(id).update("imageUrl", "")
        }
    }

    /** --- Birth date --- */

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        // Start the calendar at the current birth date, if there is one
        runCatching { birthDateFormat.parse(birthDateInput.text.toString()) }.getOrNull()
            ?.let { calendar.time = it }

        DatePickerDialog(this, { _, year, month, day ->
            val picked = Calendar.getInstance().apply { set(year, month, day) }
            birthDateInput.setText(birthDateFormat.format(picked.time))
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.maxDate = System.currentTimeMillis() // no future birth dates
            show()
        }
    }
}
