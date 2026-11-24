package com.elizabethwhitebaker.egidtracker

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Deletes everything an account owns, in Firestore and on this device. The caller deletes the
 * Firebase Auth login afterwards (it must be signed in recently; see AddCaregiverActivity).
 *
 * Order matters: firestore.rules only allow deleting a child's records while the child itself
 * still exists, so the records go first, then the children, caregivers, profile and username.
 * If any step fails nothing later runs, and since the login still exists the user can retry.
 */
object AccountDeletion {

    /** Subcollections of Children/{childId}. */
    private val CHILD_SUBCOLLECTIONS = listOf("Symptom Scores", "Quality of Life Scores", "EndoscopyResults")

    /** Top-level collections whose documents point at a child through their childId field. */
    private val CHILD_RECORD_COLLECTIONS = listOf("Medications", "Allergens", "AccidentalExposure")

    // Firestore allows at most 500 writes per batch
    private const val BATCH_SIZE = 450

    fun deleteAllData(context: Context, uid: String): Task<Void> {
        val db = Firebase.firestore
        val appContext = context.applicationContext

        return db.collection("Children").whereEqualTo("parentUserId", uid).get()
            .continueWithTask { childrenTask ->
                val children = childrenTask.result.documents

                // 1. Each child's health records, and the account's custom resources
                val recordQueries = children.flatMap { child ->
                    CHILD_SUBCOLLECTIONS.map { child.reference.collection(it).get() } +
                        db.collection("FoodEntries").document(child.id).collection("Entries").get() +
                        CHILD_RECORD_COLLECTIONS.map { db.collection(it).whereEqualTo("childId", child.id).get() }
                } + db.collection("CustomResources").whereEqualTo("userId", uid).get()

                Tasks.whenAllSuccess<QuerySnapshot>(recordQueries)
                    .continueWithTask { records ->
                        deleteInBatches(records.result.flatMap { snap -> snap.documents.map { it.reference } })
                    }
                    // 2. Children, caregivers, profile and username
                    .continueWithTask { deleted ->
                        deleted.result // rethrows if step 1 failed
                        deleteOwnerDocuments(appContext, uid, children)
                    }
                    // 3. Files kept on this device: saved documents and photos
                    .onSuccessTask {
                        val dbHelper = DBHelper(appContext)
                        children.forEach { child ->
                            dbHelper.deleteDocumentsForChild(appContext, child.id)
                            Photos.deleteIfOwned(appContext, child.getString("imageUrl"))
                        }
                        dbHelper.close()
                        Tasks.forResult<Void>(null)
                    }
            }
    }

    private fun deleteOwnerDocuments(
        context: Context, uid: String, children: List<DocumentSnapshot>
    ): Task<Void> {
        val db = Firebase.firestore
        val caregivers = db.collection("Caregivers").whereEqualTo("parentUserId", uid).get()
        val profile = db.collection("Users").document(uid).get()

        return Tasks.whenAllSuccess<Any>(caregivers, profile).continueWithTask {
            val refs = mutableListOf<DocumentReference>()
            refs += children.map { it.reference }
            refs += caregivers.result.documents.map { it.reference }
            val profileDoc = profile.result
            if (profileDoc.exists()) {
                refs += profileDoc.reference
                // Free the username so someone else can use it
                profileDoc.getString("username")
                    ?.takeIf { Usernames.isValid(it) }
                    ?.let { refs += Usernames.ref(it) }
            }
            deleteInBatches(refs).onSuccessTask {
                // Only once the records are gone, so a failed run doesn't leave broken photos
                caregivers.result.documents.forEach { Photos.deleteIfOwned(context, it.getString("imageUrl")) }
                Tasks.forResult<Void>(null)
            }
        }
    }

    private fun deleteInBatches(refs: List<DocumentReference>): Task<Void> {
        val db = Firebase.firestore
        val commits = refs.chunked(BATCH_SIZE).map { chunk ->
            db.batch().apply { chunk.forEach { delete(it) } }.commit()
        }
        return Tasks.whenAll(commits)
    }
}
