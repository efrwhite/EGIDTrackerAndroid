package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * The account's children and caregivers. Tapping a child selects them (see CurrentChild) and
 * returns to Home; Edit opens their profile.
 */
class ProfilesActivity : AppCompatActivity() {

    private lateinit var childRows: LinearLayout
    private lateinit var caregiverRows: LinearLayout

    // Child birth dates are stored in this format (see AddChildActivity)
    private val birthDateFormat = SimpleDateFormat("M/d/yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profiles)
        setupPageHeader(R.string.profiles)

        childRows = findViewById(R.id.childRows)
        caregiverRows = findViewById(R.id.caregiverRows)

        findViewById<View>(R.id.addChildlink).setOnClickListener {
            startActivity(Intent(this, AddChildActivity::class.java))
        }
        findViewById<View>(R.id.addCaregiverlink).setOnClickListener {
            startActivity(Intent(this, AddCaregiverActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Also picks up changes made on the profile screens
        loadChildren()
        loadCaregivers()
    }

    private fun loadChildren() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Firebase.firestore.collection("Children")
            .whereEqualTo("parentUserId", userId)
            .get()
            .addOnSuccessListener { documents ->
                val currentChildId = CurrentChild.getId(this)
                val children = documents.documents.sortedBy { it.getString("firstName").orEmpty().lowercase() }
                childRows.removeAllViews()
                children.forEach { doc ->
                    val firstName = doc.getString("firstName").orEmpty()
                    val isSelected = doc.id == currentChildId
                    // Keep the stored name in step with renames made elsewhere
                    if (isSelected) CurrentChild.setName(this, firstName)
                    addRow(childRows, doc, detail = childDetail(doc), isSelected = isSelected,
                        onClick = { selectChild(doc.id, firstName, isSelected) },
                        onEdit = {
                            startActivity(Intent(this, AddChildActivity::class.java).apply {
                                putExtra("childId", doc.id)
                                putExtra("editMode", true)
                            })
                        })
                }
                // Choosing between children only makes sense with more than one
                findViewById<View>(R.id.childrenHint).visibility = if (children.size > 1) View.VISIBLE else View.GONE
                showList(childRows, findViewById(R.id.childrenEmpty), children.isNotEmpty())
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load children: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadCaregivers() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Firebase.firestore.collection("Caregivers")
            .whereEqualTo("parentUserId", userId)
            .get()
            .addOnSuccessListener { documents ->
                // The account holder first, then the others by name
                val caregivers = documents.documents.sortedWith(
                    compareBy<DocumentSnapshot> { it.getBoolean("isAccountHolder") != true }
                        .thenBy { it.getString("firstName").orEmpty().lowercase() })
                caregiverRows.removeAllViews()
                caregivers.forEach { doc ->
                    val edit = {
                        startActivity(Intent(this, AddCaregiverActivity::class.java).apply {
                            putExtra("caregiverId", doc.id)
                            putExtra("editMode", true)
                        })
                    }
                    val detail = if (doc.getBoolean("isAccountHolder") == true) getString(R.string.account_holder_you) else null
                    // Nothing to select for a caregiver, so the whole row opens their profile
                    addRow(caregiverRows, doc, detail = detail, isSelected = false, onClick = edit, onEdit = edit)
                }
                showList(caregiverRows, findViewById(R.id.caregiversEmpty), caregivers.isNotEmpty())
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load caregivers: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addRow(
        container: ViewGroup, doc: DocumentSnapshot, detail: String?, isSelected: Boolean,
        onClick: () -> Unit, onEdit: () -> Unit
    ) {
        val row = layoutInflater.inflate(R.layout.view_profile_row, container, false)
        val firstName = doc.getString("firstName").orEmpty().ifBlank { "No name" }
        val fullName = listOfNotNull(firstName, doc.getString("lastName")?.takeIf { it.isNotBlank() }).joinToString(" ")

        row.findViewById<TextView>(R.id.profileName).text = fullName
        row.findViewById<TextView>(R.id.profileDetail).apply {
            text = detail
            visibility = if (detail.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        row.findViewById<View>(R.id.profileBadge).visibility = if (isSelected) View.VISIBLE else View.GONE

        // The photo, or the first initial on the salmon square
        val photo = row.findViewById<ImageView>(R.id.profilePhoto)
        val initial = row.findViewById<TextView>(R.id.profileInitial)
        val imageUrl = doc.getString("imageUrl")
        if (imageUrl.isNullOrEmpty()) {
            initial.text = firstName.take(1).uppercase()
        } else {
            Glide.with(this).load(imageUrl).centerCrop().into(photo)
            initial.visibility = View.GONE
        }

        row.setOnClickListener { onClick() }
        row.findViewById<TextView>(R.id.profileEdit).apply {
            contentDescription = getString(R.string.edit) + " " + fullName
            setOnClickListener { onEdit() }
        }
        container.addView(row)
    }

    private fun showList(rows: View, emptyView: View, hasRows: Boolean) {
        rows.visibility = if (hasRows) View.VISIBLE else View.GONE
        emptyView.visibility = if (hasRows) View.GONE else View.VISIBLE
    }

    /** e.g. "Age 5 · Diet 1"; either part is left out when unknown. */
    private fun childDetail(doc: DocumentSnapshot): String? {
        val age = doc.getString("birthDate")
            ?.let { runCatching { birthDateFormat.parse(it) }.getOrNull() }
            ?.let { born ->
                val birth = Calendar.getInstance().apply { time = born }
                val now = Calendar.getInstance()
                var years = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
                val beforeBirthday = now.get(Calendar.MONTH) < birth.get(Calendar.MONTH) ||
                    (now.get(Calendar.MONTH) == birth.get(Calendar.MONTH) &&
                        now.get(Calendar.DAY_OF_MONTH) < birth.get(Calendar.DAY_OF_MONTH))
                if (beforeBirthday) years--
                if (years < 1) getString(R.string.age_under_one) else getString(R.string.age_years, years)
            }
        val diet = doc.getString("diet")?.takeIf { it.isNotBlank() }
        return listOfNotNull(age, diet).joinToString(" · ").ifEmpty { null }
    }

    private fun selectChild(childId: String, firstName: String, isSelected: Boolean) {
        if (!isSelected) CurrentChild.set(this, childId, firstName)
        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
