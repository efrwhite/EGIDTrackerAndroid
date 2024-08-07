package com.elizabethwhitebaker.egidtracker

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import android.content.ContentValues
import java.util.*

class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        const val DATABASE_NAME = "Documents.db"
        const val DATABASE_VERSION = 1

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

                documents.add(DocumentsActivity.Document(name, url, type, size, date, thumbnail))
            }
        }
        return documents
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
