package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var childNameTextView: TextView
    private lateinit var childDietTextView: TextView
    private lateinit var childImageView: ImageView
    private lateinit var loggedStreakStat: TextView
    private lateinit var dayStreakStat: TextView
    private lateinit var symptomScoreStat: TextView

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { Firebase.firestore }

    // Same day format the food tracker stores entries under
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

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
    }

    override fun onResume() {
        super.onResume()
        val uid = auth.currentUser?.uid ?: return
        ensureValidChildSelected(uid) // reload after possible changes
    }

    private fun initializeViews() {
        childNameTextView = findViewById(R.id.childName)
        childDietTextView = findViewById(R.id.childDiet)
        childImageView = findViewById(R.id.homeImage)
        loggedStreakStat = findViewById(R.id.loggedStreakStat)
        dayStreakStat = findViewById(R.id.dayStreakStat)
        symptomScoreStat = findViewById(R.id.symptomScoreStat)

        fun opens(id: Int, screen: Class<*>) =
            findViewById<View>(id).setOnClickListener { startActivity(Intent(this, screen)) }

        opens(R.id.childCard, ProfilesActivity::class.java)
        opens(R.id.planButton, PlanActivity::class.java)
        opens(R.id.foodTrackerButton, FoodTrackerActivity::class.java)
        opens(R.id.symptomCheckerButton, SymptomCheckerActivity::class.java)
        opens(R.id.QoLButton, QoLActivity::class.java)
        opens(R.id.EOEedButton, EducationActivity::class.java)

        findViewById<View>(R.id.logoutButton).setOnClickListener { confirmLogout() }
    }

    /** --- Selected child --- */

    private fun ensureValidChildSelected(uid: String) {
        val childId = CurrentChild.getId(this)
        if (childId == null) {
            // No selection saved yet — choose a default for this user
            selectDefaultChildForUser(uid)
            return
        }

        // Load the child, but verify it belongs to the current user
        db.collection("Children").document(childId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists() || doc.getString("parentUserId") != uid) {
                    // Stale id, or someone else's child — pick a default instead
                    CurrentChild.clear(this)
                    selectDefaultChildForUser(uid)
                    return@addOnSuccessListener
                }
                bindChild(doc.id, doc.getString("firstName"), doc.getString("lastName"),
                    doc.getString("diet"), doc.getString("imageUrl"))
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load child data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun selectDefaultChildForUser(uid: String) {
        db.collection("Children")
            .whereEqualTo("parentUserId", uid)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val childDoc = snap.documents.firstOrNull()
                if (childDoc == null) {
                    bindChild(null, null, null, null, null)
                    return@addOnSuccessListener
                }
                CurrentChild.set(this, childDoc.id, childDoc.getString("firstName"))
                bindChild(childDoc.id, childDoc.getString("firstName"), childDoc.getString("lastName"),
                    childDoc.getString("diet"), childDoc.getString("imageUrl"))
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to select a child: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun bindChild(childId: String?, firstName: String?, lastName: String?, diet: String?, imageUrl: String?) {
        val name = listOfNotNull(firstName, lastName).joinToString(" ").trim()
        childNameTextView.text = name.ifEmpty { getString(R.string.no_child_selected) }

        childDietTextView.text = diet
        childDietTextView.visibility = if (diet.isNullOrBlank()) View.GONE else View.VISIBLE

        // No photo: the card shows the plain salmon avatar from the design
        if (imageUrl.isNullOrEmpty()) {
            Glide.with(this).clear(childImageView)
            childImageView.setImageDrawable(null)
        } else {
            Glide.with(this).load(imageUrl).into(childImageView)
        }

        loadStats(childId)
    }

    /** --- Stats --- */

    /**
     * Fills the stats pill from the child's food entries and symptom checks:
     *  - logged streak: days in the last 7 (including today) with at least one entry
     *  - day streak: consecutive days with an entry, ending today (or yesterday, so the streak
     *    isn't lost before today's entry is made)
     *  - symptom score: total of the most recent Symptom Score Checker result
     */
    private fun loadStats(childId: String?) {
        showStats(null, null, null)
        if (childId == null) return

        val yearAgo = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }
        val foodEntries = db.collection("FoodEntries").document(childId).collection("Entries")
            .whereGreaterThanOrEqualTo("date", dayFormat.format(yearAgo.time))
            .get()
        val symptomScores = db.collection("Children").document(childId).collection("Symptom Scores")
            .orderBy("date", Query.Direction.DESCENDING)
            .get()

        Tasks.whenAllSuccess<QuerySnapshot>(foodEntries, symptomScores)
            .addOnSuccessListener { (food, symptoms) ->
                // Ignore results for a child that's no longer selected
                if (CurrentChild.getId(this) != childId) return@addOnSuccessListener

                val loggedDays = food.documents.mapNotNull { it.getString("date") }.toMutableSet()
                symptoms.documents.mapNotNullTo(loggedDays) { doc ->
                    doc.getTimestamp("date")?.let { dayFormat.format(it.toDate()) }
                }
                val latestScore = symptoms.documents.firstOrNull()?.get("totalScore") as? Number

                showStats(countLoggedLastWeek(loggedDays), currentStreak(loggedDays), latestScore?.toInt())
            }
            .addOnFailureListener { /* leave the dashes in place */ }
    }

    private fun dayKey(daysAgo: Int): String =
        dayFormat.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }.time)

    private fun countLoggedLastWeek(days: Set<String>) = (0 until 7).count { dayKey(it) in days }

    private fun currentStreak(days: Set<String>): Int {
        var daysAgo = if (dayKey(0) in days) 0 else 1
        var streak = 0
        while (dayKey(daysAgo) in days) {
            streak++
            daysAgo++
        }
        return streak
    }

    private fun showStats(loggedThisWeek: Int?, streak: Int?, symptomScore: Int?) {
        val empty = getString(R.string.stat_empty)
        setStat(loggedStreakStat,
            loggedThisWeek?.let { getString(R.string.stat_logged_value, it) } ?: empty,
            R.string.stat_logged_streak)
        setStat(dayStreakStat, streak?.toString() ?: empty, R.string.stat_day_streak)
        setStat(symptomScoreStat, symptomScore?.toString() ?: empty, R.string.stat_symptom_score)
    }

    private fun setStat(view: TextView, value: String, labelRes: Int) {
        view.text = buildSpannedString {
            bold { append(value) }
            append("\n")
            append(getString(labelRes))
        }
    }

    /** --- Logout: also clear this user's selected child id --- */

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.log_out_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.logOut) { _, _ -> logout() }
            .show()
    }

    private fun logout() {
        CurrentChild.clear(this) // must run before signOut(); keys are per-uid

        auth.signOut()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
