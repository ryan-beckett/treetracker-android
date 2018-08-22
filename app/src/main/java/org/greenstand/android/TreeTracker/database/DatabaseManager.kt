package org.greenstand.android.TreeTracker.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/**
 * Created by lei on 11/9/17.
 */

class DatabaseManager private constructor(private val mDbHelper: DbHelper) {
    private var mDatabase: SQLiteDatabase? = null
    private var mOpenCounter: Int = 0

    @Synchronized
    fun openDatabase(): SQLiteDatabase? {
        mOpenCounter++
        if (mOpenCounter == 1) {
            // Opening new database
            mDatabase = mDbHelper.writableDatabase
        }
        return mDatabase
    }

    @Synchronized
    fun closeDatabase() {
        mOpenCounter--
        if (mOpenCounter == 0) {
            // Closing database
            mDatabase!!.close()
        }
    }

    fun queryCursor(sql: String, selectionArgs: Array<String>?): Cursor {
        return mDatabase!!.rawQuery(sql, selectionArgs)
    }

    fun insert(table: String, nullColumnHack: String?, contentValues: ContentValues): Long {
        return mDatabase!!.insert(table, nullColumnHack, contentValues)
    }

    fun update(table: String, values: ContentValues, whereClause: String, whereArgs: Array<String>): Int {
        return mDatabase!!.update(table, values, whereClause, whereArgs)
    }

    fun delete(table: String, whereClause: String, whereArgs: Array<String?>): Int {
        return mDatabase!!.delete(table, whereClause, whereArgs)
    }

    companion object {

        private var sInstance: DatabaseManager? = null

        @Synchronized
        fun getInstance(dbHelper: DbHelper): DatabaseManager {
            if (sInstance == null) {
                sInstance = DatabaseManager(dbHelper)
            }

            return sInstance as DatabaseManager
        }
    }
}
