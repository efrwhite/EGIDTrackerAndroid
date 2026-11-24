package com.elizabethwhitebaker.egidtracker

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
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
                val recordQueries = children.flatMap { childRecordQueries(it) } +
                    db.collection("CustomResources").whereEqualTo("userId", uid).get()

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
                        deleteLocalChildData(appContext, children)
                        Tasks.forResult<Void>(null)
                    }
            }
    }

    /**
     * Deletes one child: their records first (the rules only allow that while the child
     * exists), then the child, then their documents and photo on this device.
     */
    fun deleteChild(context: Context, child: DocumentSnapshot): Task<Void> {
        val appContext = context.applicationContext
        return Tasks.whenAllSuccess<QuerySnapshot>(childRecordQueries(child))
            .continueWithTask { records ->
                deleteInBatches(records.result.flatMap { snap -> snap.documents.map { it.reference } })
            }
            // Separately, after: batches run in parallel, and records left in a batch that
            // landed after the child's would be refused
            .onSuccessTask { child.reference.delete() }
            .onSuccessTask {
                deleteLocalChildData(appContext, listOf(child))
                Tasks.forResult<Void>(null)
            }
    }

    /**
     * Queries for everything recorded about [child]: scores, food log, medications, etc. From the
     * server only: the phone's copy may be missing records, which would be left behind
     * unreachable once the child is gone. Offline, this fails instead.
     */
    private fun childRecordQueries(child: DocumentSnapshot): List<Task<QuerySnapshot>> {
        val db = Firebase.firestore
        return CHILD_SUBCOLLECTIONS.map { child.reference.collection(it).get(Source.SERVER) } +
            db.collection("FoodEntries").document(child.id).collection("Entries").get(Source.SERVER) +
            CHILD_RECORD_COLLECTIONS.map { db.collection(it).whereEqualTo("childId", child.id).get(Source.SERVER) }
    }

    /** Saved documents and photos kept on this device for [children]. */
    private fun deleteLocalChildData(context: Context, children: List<DocumentSnapshot>) {
        val dbHelper = DBHelper(context)
        children.forEach { child ->
            dbHelper.deleteDocumentsForChild(context, child.id)
            Photos.deleteIfOwned(context, child.getString("imageUrl"))
        }
        dbHelper.close()
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
