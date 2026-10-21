package com.elizabethwhitebaker.egidtracker

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Username -> login email lookup, stored as Usernames/{username} = { uid, email }.
 *
 * Users log in with a username, but Firebase Auth signs in with an email, so the app has to
 * find the email before the user is signed in. firestore.rules lets anyone read one exact
 * Usernames document but not list them, which keeps Users profiles private.
 */
object Usernames {

    private const val COLLECTION = "Usernames"

    // Also keeps usernames safe to use as Firestore document IDs (no "/", not "." or "__x__")
    private val VALID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$")

    fun isValid(username: String) = VALID.matches(username)

    fun ref(username: String): DocumentReference =
        Firebase.firestore.collection(COLLECTION).document(username)

    /** The login email for [username], or null if there is no such username. */
    fun lookupEmail(username: String): Task<String?> {
        if (!isValid(username)) return Tasks.forResult(null)
        return ref(username).get().continueWith { task -> task.result.getString("email") }
    }

    fun entry(uid: String, email: String) = mapOf("uid" to uid, "email" to email)
}
