package com.elizabethwhitebaker.egidtracker

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.tasks.Task

/**
 * How long to wait for the server to confirm a save before treating it as queued.
 *
 * Firestore keeps a write made offline on the device and sends it when the connection returns,
 * but the write's Task only completes once the server confirms, which never happens while
 * offline. Waiting on it would leave the screen stuck with its Save button disabled.
 */
private const val SERVER_WAIT_MS = 2500L

/**
 * Waits briefly for [write] to reach the server, then calls [onSaved] with queued = false; if
 * the server hasn't answered in time (offline or a slow connection), calls it with
 * queued = true and the write is sent later. [onFailed] covers failures reported in time; a
 * queued write that fails later shows a toast, since the screen has usually closed by then.
 */
fun Activity.awaitSave(write: Task<*>, onSaved: (queued: Boolean) -> Unit, onFailed: (Exception) -> Unit) {
    val handler = Handler(Looper.getMainLooper())
    var settled = false
    val treatAsQueued = Runnable {
        if (!settled) {
            settled = true
            onSaved(true)
        }
    }
    handler.postDelayed(treatAsQueued, SERVER_WAIT_MS)

    val appContext = applicationContext
    write.addOnCompleteListener { task ->
        if (settled) {
            if (!task.isSuccessful) Toast.makeText(appContext, R.string.sync_failed, Toast.LENGTH_LONG).show()
            return@addOnCompleteListener
        }
        settled = true
        handler.removeCallbacks(treatAsQueued)
        if (task.isSuccessful) onSaved(false) else onFailed(task.exception ?: Exception("Save failed"))
    }
}

/** The usual toast after [awaitSave]: [message], or the offline note when the save is queued. */
fun Activity.toastSaved(message: String, queued: Boolean) {
    Toast.makeText(this, if (queued) getString(R.string.saved_offline) else message, Toast.LENGTH_SHORT).show()
}
