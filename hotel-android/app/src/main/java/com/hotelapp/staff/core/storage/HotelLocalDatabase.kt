package com.hotelapp.staff.core.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HotelLocalDatabase(
    context: Context,
) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION), HotelLocalStore {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cache_entries (
                cache_key TEXT PRIMARY KEY,
                payload_json TEXT NOT NULL,
                updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pending_mutations (
                id TEXT PRIMARY KEY,
                kind TEXT NOT NULL,
                target_id INTEGER NOT NULL,
                payload_json TEXT NOT NULL,
                created_at_ms INTEGER NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                last_error TEXT
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override suspend fun putCache(key: String, payloadJson: String, nowMs: Long) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("cache_key", key)
                put("payload_json", payloadJson)
                put("updated_at_ms", nowMs)
            }
            writableDatabase.insertWithOnConflict("cache_entries", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    override suspend fun getCache(key: String): StoredCacheEntry? = withContext(Dispatchers.IO) {
        readableDatabase.query(
            "cache_entries",
            arrayOf("cache_key", "payload_json", "updated_at_ms"),
            "cache_key = ?",
            arrayOf(key),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                StoredCacheEntry(
                    cacheKey = cursor.getString(0),
                    payloadJson = cursor.getString(1),
                    updatedAtMs = cursor.getLong(2),
                )
            } else {
                null
            }
        }
    }

    override suspend fun enqueueMutation(mutation: StoredPendingMutation) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("id", mutation.id)
                put("kind", mutation.kind)
                put("target_id", mutation.targetId)
                put("payload_json", mutation.payloadJson)
                put("created_at_ms", mutation.createdAtMs)
                put("attempts", mutation.attempts)
                put("last_error", mutation.lastError)
            }
            writableDatabase.insertWithOnConflict("pending_mutations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    override suspend fun listPendingMutations(): List<StoredPendingMutation> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StoredPendingMutation>()
        readableDatabase.query(
            "pending_mutations",
            arrayOf("id", "kind", "target_id", "payload_json", "created_at_ms", "attempts", "last_error"),
            null,
            null,
            null,
            null,
            "created_at_ms ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    StoredPendingMutation(
                        id = cursor.getString(0),
                        kind = cursor.getString(1),
                        targetId = cursor.getLong(2),
                        payloadJson = cursor.getString(3),
                        createdAtMs = cursor.getLong(4),
                        attempts = cursor.getInt(5),
                        lastError = if (cursor.isNull(6)) null else cursor.getString(6),
                    ),
                )
            }
        }
        result
    }

    override suspend fun deleteMutation(id: String) {
        withContext(Dispatchers.IO) {
            writableDatabase.delete("pending_mutations", "id = ?", arrayOf(id))
        }
    }

    override suspend fun recordMutationFailure(id: String, error: String) {
        withContext(Dispatchers.IO) {
            writableDatabase.execSQL(
                "UPDATE pending_mutations SET attempts = attempts + 1, last_error = ? WHERE id = ?",
                arrayOf<Any>(error, id),
            )
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            writableDatabase.delete("cache_entries", null, null)
            writableDatabase.delete("pending_mutations", null, null)
        }
    }

    companion object {
        private const val DB_NAME = "hotel_staff_local.db"
        private const val DB_VERSION = 1
    }
}
