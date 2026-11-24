package com.elizabethwhitebaker.egidtracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import java.io.File
import java.util.Date

/**
 * Saved documents (uploaded files and photos) per child, kept on this device only. The files
 * themselves live in the app's storage; rows hold their path (or a Photos URI for older rows).
 */
class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        const val DATABASE_NAME = "Documents.db"
        const val DATABASE_VERSION = 1

        /** Earlier versions used this separate database, which had no child column. */
        private const val LEGACY_DATABASE_NAME = "documents.db"

        private const val SQL_CREATE_ENTRIES =
            "CREATE TABLE ${DocumentEntry.TABLE_NAME} (" +
                    "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                    "${DocumentEntry.COLUMN_NAME_CHILD_ID} TEXT," +
                    "${DocumentEntry.COLUMN_NAME_TITLE} TEXT," +
                    "${DocumentEntry.COLUMN_NAME_URL} TEXT," +
                    "${DocumentEntry.COLUMN_NAME_TYPE} TEXT," +
                    "${DocumentEntry.COLUMN_NAME_SIZE} INTEGER," +
                    "${DocumentEntry.COLUMN_NAME_DATE} INTEGER," +
                    "${DocumentEntry.COLUMN_NAME_THUMBNAIL} TEXT)"

        private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS ${DocumentEntry.TABLE_NAME}"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    fun insertDocument(childId: String, name: String, url: String, type: String, size: Long, date: Long, thumbnail: String?): Long {
        val db = writableDatabase

        val values = ContentValues().apply {
            put(DocumentEntry.COLUMN_NAME_CHILD_ID, childId)
            put(DocumentEntry.COLUMN_NAME_TITLE, name)
            put(DocumentEntry.COLUMN_NAME_URL, url)
            put(DocumentEntry.COLUMN_NAME_TYPE, type)
            put(DocumentEntry.COLUMN_NAME_SIZE, size)
            put(DocumentEntry.COLUMN_NAME_DATE, date)
            put(DocumentEntry.COLUMN_NAME_THUMBNAIL, thumbnail)
        }

        return db.insert(DocumentEntry.TABLE_NAME, null, values)
    }

    fun getAllDocuments(childId: String): List<DocumentsActivity.Document> {
        val documents = mutableListOf<DocumentsActivity.Document>()
        val db = readableDatabase
        val cursor = db.query(
            DocumentEntry.TABLE_NAME,
            null,
            "${DocumentEntry.COLUMN_NAME_CHILD_ID} = ?",
            arrayOf(childId),
            null,
            null,
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(c.getColumnIndexOrThrow(DocumentEntry.COLUMN_NAME_TITLE))
                val url = c.getString(c.getColumnIndexOrThrow(DocumentEntry.COLUMN_NAME_URL))
                val type = c.getString(c.getColumnIndexOrThrow(DocumentEntry.COLUMN_NAME_TYPE))
                val size = c.getLong(c.getColumnIndexOrThrow(DocumentEntry.COLUMN_NAME_SIZE))
                val date = Date(c.getLong(c.getColumnIndexOrThrow(DocumentEntry.COLUMN_NAME_DATE)))
                val thumbnail = c.getString(c.getColumnIndexOrThrow(DocumentEntry.COLUMN_NAME_THUMBNAIL))

                val id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID))
                documents.add(DocumentsActivity.Document(id, name, url, type, size, date, thumbnail))
            }
        }
        return documents
    }

    fun updateDocumentName(documentId: Long, newName: String) {
        val values = ContentValues().apply { put(DocumentEntry.COLUMN_NAME_TITLE, newName) }
        writableDatabase.update(DocumentEntry.TABLE_NAME, values, "${BaseColumns._ID} = ?", arrayOf(documentId.toString()))
    }

    /** Deletes one document: its file on this device and its row. */
    fun deleteDocument(context: Context, documentId: Long) {
        deleteWhere(context, "${BaseColumns._ID} = ?", arrayOf(documentId.toString()))
    }

    /** Deletes a child's saved documents: the files on this device and their rows. */
    fun deleteDocumentsForChild(context: Context, childId: String) {
        deleteWhere(context, "${DocumentEntry.COLUMN_NAME_CHILD_ID} = ?", arrayOf(childId))
    }

    private fun deleteWhere(context: Context, where: String, args: Array<String>) {
        val db = writableDatabase
        db.query(DocumentEntry.TABLE_NAME, arrayOf(DocumentEntry.COLUMN_NAME_URL), where, args, null, null, null)
            .use { c ->
                while (c.moveToNext()) deleteFile(context, c.getString(0))
            }
        db.delete(DocumentEntry.TABLE_NAME, where, args)
    }

    /** Uploaded files are stored by path; older camera photos by the app's own photo URI. */
    private fun deleteFile(context: Context, url: String?) {
        url ?: return
        if (url.startsWith("/")) File(url).delete() else Photos.deleteIfOwned(context, url)
    }

    /**
     * Moves documents from the old child-less database into this one, under [childId], then
     * deletes the old database. Earlier versions showed those documents for every child, so
     * the child who opens Documents first is the best owner we can tell.
     */
    fun migrateLegacyDocuments(context: Context, childId: String) {
        val legacyFile = context.getDatabasePath(LEGACY_DATABASE_NAME)
        // Same name ignoring case as this database: only treat it as legacy if it's a different file
        if (!legacyFile.exists() || legacyFile.canonicalPath == context.getDatabasePath(DATABASE_NAME).canonicalPath) return
        runCatching {
            SQLiteDatabase.openDatabase(legacyFile.path, null, SQLiteDatabase.OPEN_READONLY).use { legacy ->
                legacy.rawQuery("SELECT name, url, type, size, date, thumbnail FROM documents", null).use { c ->
                    while (c.moveToNext()) {
                        insertDocument(childId, c.getString(0), c.getString(1), c.getString(2),
                            c.getLong(3), c.getLong(4), c.getString(5))
                    }
                }
            }
        }
        context.deleteDatabase(LEGACY_DATABASE_NAME)
    }

    object DocumentEntry : BaseColumns {
        const val TABLE_NAME = "documents"
        const val COLUMN_NAME_CHILD_ID = "childId"
        const val COLUMN_NAME_TITLE = "title"
        const val COLUMN_NAME_URL = "url"
        const val COLUMN_NAME_TYPE = "type"
        const val COLUMN_NAME_SIZE = "size"
        const val COLUMN_NAME_DATE = "date"
        const val COLUMN_NAME_THUMBNAIL = "thumbnail"
    }
}
