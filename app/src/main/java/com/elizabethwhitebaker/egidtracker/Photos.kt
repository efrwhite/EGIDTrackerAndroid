package com.elizabethwhitebaker.egidtracker

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Child and caregiver photos, kept in the app's own Pictures folder and referred to by a
 * FileProvider URI (see res/xml/file_paths.xml). Gallery picks are copied there too: the URI the
 * gallery hands back only works until the app closes, so storing it directly loses the photo.
 *
 * The photos live on this phone only; another device signed in to the same account won't see them.
 */
object Photos {

    private const val AUTHORITY = "com.elizabethwhitebaker.egidtracker.fileprovider"

    /** A new empty file for the camera to write into, and the URI to hand to it. */
    fun newPhotoUri(context: Context): Uri = uriFor(context, newPhotoFile(context))

    /** Copies a gallery pick into app storage; returns its lasting URI, or null if unreadable. */
    fun copyToAppStorage(context: Context, source: Uri): Uri? = runCatching {
        val file = newPhotoFile(context)
        context.contentResolver.openInputStream(source)!!.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        uriFor(context, file)
    }.getOrNull()

    /** Deletes the file behind [url] if it's one of this app's photos; ignores anything else. */
    fun deleteIfOwned(context: Context, url: String?) {
        val uri = url?.takeIf { it.isNotEmpty() }?.let(Uri::parse) ?: return
        // "my_images" is the app's Pictures folder in res/xml/file_paths.xml
        if (uri.authority != AUTHORITY || uri.pathSegments.size != 2 || uri.pathSegments[0] != "my_images") return
        val name = uri.lastPathSegment ?: return
        File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), name).delete()
    }

    private fun newPhotoFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg",
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES))
    }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, AUTHORITY, file)
}
