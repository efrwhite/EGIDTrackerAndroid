package com.elizabethwhitebaker.egidtracker

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Locale

/**
 * Username -> login email lookup, stored as Usernames/{username} = { uid, email }.
 *
 * Users log in with a username, but Firebase Auth signs in with an email, so the app has to
 * find the email before the user is signed in. firestore.rules lets anyone read one exact
 * Usernames document but not list them, which keeps Users profiles private.
 */
object Usernames {

    private const val COLLECTION = "Usernames"

    const val MAX_LENGTH = 30

    // Also keeps usernames safe to use as Firestore document IDs (no "/", not "." or "__x__")
    private val VALID = Regex("^[a-z0-9][a-z0-9._-]{2,${MAX_LENGTH - 1}}$")

    /**
     * Usernames are case-insensitive: they're stored lowercase, and whatever the user types is
     * lowercased the same way, so "Jane" and "jane" are one account. Pass input through this
     * before [isValid] or [ref]; [lookupEmail] does it itself.
     */
    fun normalize(input: String) = input.trim().lowercase(Locale.ROOT)

    fun isValid(username: String) = VALID.matches(username)

    fun ref(username: String): DocumentReference =
        Firebase.firestore.collection(COLLECTION).document(username)

    /** The login email for the username typed as [input], or null if there is no such username. */
    fun lookupEmail(input: String): Task<String?> {
        val username = normalize(input)
        if (!isValid(username)) return Tasks.forResult(null)
        return ref(username).get().continueWith { task -> task.result.getString("email") }
    }

    fun entry(uid: String, email: String) = mapOf("uid" to uid, "email" to email)
}
