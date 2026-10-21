package com.elizabethwhitebaker.egidtracker

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

/**
 * Single source of truth for the selected child. The selection is stored in
 * SharedPreferences under keys namespaced by the signed-in user's uid, so one
 * account never sees another account's child after switching users.
 */
object CurrentChild {

    private const val PREFS_NAME = "AppPreferences"

    // Pre-namespacing keys; removed whenever the selection is written or cleared.
    private const val LEGACY_ID_KEY = "CurrentChildId"
    private const val LEGACY_NAME_KEY = "CurrentChildName"

    private fun idKey(uid: String) = "CurrentChildId_$uid"
    private fun nameKey(uid: String) = "CurrentChildName_$uid"

    private fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getId(context: Context): String? {
        val uid = currentUid() ?: return null
        return prefs(context).getString(idKey(uid), null)
    }

    fun getName(context: Context): String? {
        val uid = currentUid() ?: return null
        return prefs(context).getString(nameKey(uid), null)
    }

    /** Saves the selected child. Pass [name] when known; otherwise the stored name is left as is. */
    fun set(context: Context, childId: String, name: String? = null) {
        val uid = currentUid() ?: return
        prefs(context).edit().apply {
            putString(idKey(uid), childId)
            if (name != null) putString(nameKey(uid), name)
            remove(LEGACY_ID_KEY)
            remove(LEGACY_NAME_KEY)
        }.apply()
    }

    fun setName(context: Context, name: String) {
        val uid = currentUid() ?: return
        prefs(context).edit().putString(nameKey(uid), name).apply()
    }

    fun clear(context: Context) {
        val uid = currentUid() ?: return
        prefs(context).edit()
            .remove(idKey(uid))
            .remove(nameKey(uid))
            .remove(LEGACY_ID_KEY)
            .remove(LEGACY_NAME_KEY)
            .apply()
    }
}
