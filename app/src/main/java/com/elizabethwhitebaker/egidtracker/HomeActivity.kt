package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class HomeActivity : AppCompatActivity() {

    private lateinit var profileButton: Button
    private lateinit var planButton: Button
    private lateinit var foodTrackerButton: Button
    private lateinit var symptomCheckerButton: Button
    private lateinit var qoLButton: Button
    private lateinit var eOEedButton: Button
    private lateinit var logoutButton: Button
    private lateinit var childNameTextView: TextView
    private lateinit var childDietTextView: TextView
    private lateinit var childImageView: ImageView

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { Firebase.firestore }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        initializeViews()

        val uid = auth.currentUser?.uid
        if (uid == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        ensureValidChildSelected(uid)
        setupLogout()
    }

    override fun onResume() {
        super.onResume()
        val uid = auth.currentUser?.uid ?: return
        ensureValidChildSelected(uid) // reload after possible changes
    }

    private fun initializeViews() {
        profileButton = findViewById(R.id.profileButton)
        planButton = findViewById(R.id.planButton)
        foodTrackerButton = findViewById(R.id.foodTrackerButton)
        symptomCheckerButton = findViewById(R.id.symptomCheckerButton)
        qoLButton = findViewById(R.id.QoLButton)
        eOEedButton = findViewById(R.id.EOEedButton)
        childNameTextView = findViewById(R.id.childName)
        childDietTextView = findViewById(R.id.childDiet)
        childImageView = findViewById(R.id.homeImage)
        logoutButton = findViewById(R.id.logoutButton)

        profileButton.setOnClickListener { startActivity(Intent(this, ProfilesActivity::class.java)) }
        planButton.setOnClickListener { startActivity(Intent(this, PlanActivity::class.java)) }
        foodTrackerButton.setOnClickListener { startActivity(Intent(this, FoodTrackerActivity::class.java)) }
        symptomCheckerButton.setOnClickListener { startActivity(Intent(this, SymptomCheckerActivity::class.java)) }
        qoLButton.setOnClickListener { startActivity(Intent(this, QoLActivity::class.java)) }
        eOEedButton.setOnClickListener { startActivity(Intent(this, EducationActivity::class.java)) }
    }

    /** --- Core logic fixes --- */

    private fun ensureValidChildSelected(uid: String) {
        val childId = getCurrentChildId(uid)
        if (childId == null) {
            // No selection saved yet — choose a default for this user
            selectDefaultChildForUser(uid)
            return
        }

        // Load the child, but verify it belongs to the current user
        db.collection("Children").document(childId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    // Stale id — pick default
                    removeCurrentChildId(uid)
                    selectDefaultChildForUser(uid)
                    return@addOnSuccessListener
                }

                val parentUserId = doc.getString("parentUserId")
                if (parentUserId != uid) {
                    // Belongs to someone else — fix and choose default
                    removeCurrentChildId(uid)
                    selectDefaultChildForUser(uid)
                    return@addOnSuccessListener
                }

                // OK to display
                bindChildUI(
                    name = doc.getString("firstName") ?: "No Name",
                    diet = doc.getString("diet") ?: "No Diet Specified",
                    imageUrl = doc.getString("imageUrl") ?: ""
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load child data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun selectDefaultChildForUser(uid: String) {
        // Pick the first child for this user; order by an optional timestamp if you have one
        db.collection("Children")
            .whereEqualTo("parentUserId", uid)
            // If you store createdAt/updatedAt, you can order. Otherwise, just limit(1).
            //.orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    // No children — clear UI to defaults
                    bindChildUI("No Child", "No Diet Specified", "")
                    return@addOnSuccessListener
                }

                val childDoc = snap.documents.first()
                val newChildId = childDoc.id
                setCurrentChildId(uid, newChildId)

                bindChildUI(
                    name = childDoc.getString("firstName") ?: "No Name",
                    diet = childDoc.getString("diet") ?: "No Diet Specified",
                    imageUrl = childDoc.getString("imageUrl") ?: ""
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to select a child: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun bindChildUI(name: String, diet: String, imageUrl: String) {
        childNameTextView.text = name
        childDietTextView.text = diet

        if (imageUrl.isNotEmpty()) {
            Glide.with(this).load(imageUrl).into(childImageView)
        } else {
            childImageView.setImageResource(R.drawable.default_profile_picture)
        }
    }

    /** --- Namespaced SharedPreferences helpers --- */

    private fun prefKeyFor(uid: String) = "CurrentChildId_$uid"

    private fun getCurrentChildId(uid: String): String? {
        val sp = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return sp.getString(prefKeyFor(uid), null)
    }

    fun setCurrentChildId(uid: String, childId: String) {
        val sp = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        sp.edit().putString(prefKeyFor(uid), childId).apply()
    }

    private fun removeCurrentChildId(uid: String) {
        val sp = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        sp.edit().remove(prefKeyFor(uid)).apply()
    }

    /** --- Logout: also clear this user's selected child id --- */

    private fun setupLogout() {
        logoutButton.setOnClickListener {
            val uid = auth.currentUser?.uid
            if (uid != null) removeCurrentChildId(uid)

            auth.signOut()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
