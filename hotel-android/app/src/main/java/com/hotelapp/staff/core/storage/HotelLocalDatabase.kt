package com.hotelapp.staff.core.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import com.hotelapp.staff.core.network.SecretCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HotelLocalDatabase(
    context: Context,
    private val scopeProvider: () -> LocalDataScope = { LocalDataScope() },
    private val cipher: SecretCipher? = null,
) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION), HotelLocalStore {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        runCatching {
            db.rawQuery("PRAGMA secure_delete = ON", null)?.use { it.moveToFirst() }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cache_entries (
                server_origin TEXT NOT NULL DEFAULT '',
                user_id INTEGER NOT NULL DEFAULT 0,
                cache_key TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                PRIMARY KEY (server_origin, user_id, cache_key)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pending_mutations (
                id TEXT PRIMARY KEY,
                server_origin TEXT NOT NULL DEFAULT '',
                user_id INTEGER NOT NULL DEFAULT 0,
                session_generation INTEGER NOT NULL DEFAULT 0,
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            // Legacy v1/v2 rows lack origin/user scoping or rest encryption; discard cleanly.
            db.execSQL("DROP TABLE IF EXISTS cache_entries")
            db.execSQL("DROP TABLE IF EXISTS pending_mutations")
            onCreate(db)
        }
    }

    private fun encodePayload(plain: String): String {
        val activeCipher = cipher ?: return plain
        val encrypted = activeCipher.encrypt(plain.toByteArray(Charsets.UTF_8))
        return ENCRYPTED_PREFIX + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decodePayload(stored: String): String? {
        val activeCipher = cipher ?: return stored
        if (!stored.startsWith(ENCRYPTED_PREFIX)) {
            return null
        }
        return runCatching {
            val raw = Base64.decode(stored.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
            String(activeCipher.decrypt(raw), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun resolveCacheScope(key: String): LocalDataScope? {
        if (key == GLOBAL_WIDGET_CACHE_KEY) return LocalDataScope()
        val scope = scopeProvider()
        return if (scope.userId > 0L) scope else null
    }

    override suspend fun putCache(key: String, payloadJson: String, nowMs: Long) {
        withContext(Dispatchers.IO) {
            val scope = resolveCacheScope(key) ?: return@withContext
            val values = ContentValues().apply {
                put("server_origin", scope.serverOrigin)
                put("user_id", scope.userId)
                put("cache_key", key)
                put("payload_json", encodePayload(payloadJson))
                put("updated_at_ms", nowMs)
            }
            writableDatabase.insertWithOnConflict("cache_entries", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    override suspend fun getCache(key: String): StoredCacheEntry? = withContext(Dispatchers.IO) {
        val scope = resolveCacheScope(key) ?: return@withContext null
        var deleteCorrupt = false
        val entry = readableDatabase.query(
            "cache_entries",
            arrayOf("cache_key", "payload_json", "updated_at_ms"),
            "server_origin = ? AND user_id = ? AND cache_key = ?",
            arrayOf(scope.serverOrigin, scope.userId.toString(), key),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val decoded = decodePayload(cursor.getString(1))
                if (decoded == null) {
                    deleteCorrupt = true
                    null
                } else {
                    StoredCacheEntry(
                        cacheKey = cursor.getString(0),
                        payloadJson = decoded,
                        updatedAtMs = cursor.getLong(2),
                    )
                }
            } else {
                null
            }
        }
        if (deleteCorrupt) {
            writableDatabase.delete(
                "cache_entries",
                "server_origin = ? AND user_id = ? AND cache_key = ?",
                arrayOf(scope.serverOrigin, scope.userId.toString(), key),
            )
        }
        entry
    }

    override suspend fun enqueueMutation(mutation: StoredPendingMutation) {
        withContext(Dispatchers.IO) {
            val scope = scopeProvider()
            val origin = mutation.serverOrigin.ifBlank { scope.serverOrigin }
            val uid = if (mutation.userId > 0L) mutation.userId else scope.userId
            if (uid <= 0L) return@withContext
            val values = ContentValues().apply {
                put("id", mutation.id)
                put("server_origin", origin)
                put("user_id", uid)
                put("session_generation", mutation.sessionGeneration)
                put("kind", mutation.kind)
                put("target_id", mutation.targetId)
                put("payload_json", encodePayload(mutation.payloadJson))
                put("created_at_ms", mutation.createdAtMs)
                put("attempts", mutation.attempts)
                put("last_error", mutation.lastError)
            }
            writableDatabase.insertWithOnConflict("pending_mutations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    override suspend fun listPendingMutations(): List<StoredPendingMutation> = withContext(Dispatchers.IO) {
        val scope = scopeProvider()
        if (scope.userId <= 0L) return@withContext emptyList()
        val result = mutableListOf<StoredPendingMutation>()
        val corruptIds = mutableListOf<String>()
        readableDatabase.query(
            "pending_mutations",
            arrayOf("id", "kind", "target_id", "payload_json", "created_at_ms", "attempts", "last_error", "server_origin", "user_id", "session_generation"),
            "server_origin = ? AND user_id = ?",
            arrayOf(scope.serverOrigin, scope.userId.toString()),
            null,
            null,
            "created_at_ms ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val decoded = decodePayload(cursor.getString(3))
                if (decoded == null) {
                    corruptIds.add(id)
                    continue
                }
                result.add(
                    StoredPendingMutation(
                        id = id,
                        kind = cursor.getString(1),
                        targetId = cursor.getLong(2),
                        payloadJson = decoded,
                        createdAtMs = cursor.getLong(4),
                        attempts = cursor.getInt(5),
                        lastError = if (cursor.isNull(6)) null else cursor.getString(6),
                        serverOrigin = cursor.getString(7),
                        userId = cursor.getLong(8),
                        sessionGeneration = cursor.getLong(9),
                    ),
                )
            }
        }
        corruptIds.forEach { badId ->
            writableDatabase.delete("pending_mutations", "id = ?", arrayOf(badId))
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

    override suspend fun purgeOtherScopes(activeOrigin: String, activeUserId: Long) {
        withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete(
                    "cache_entries",
                    "cache_key != ? AND (server_origin != ? OR user_id != ?)",
                    arrayOf(GLOBAL_WIDGET_CACHE_KEY, activeOrigin, activeUserId.toString()),
                )
                db.delete(
                    "pending_mutations",
                    "server_origin != ? OR user_id != ?",
                    arrayOf(activeOrigin, activeUserId.toString()),
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete("cache_entries", null, null)
                db.delete("pending_mutations", null, null)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            runCatching {
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)?.use { it.moveToFirst() }
            }
        }
    }

    companion object {
        private const val DB_NAME = "hotel_staff_local.db"
        private const val DB_VERSION = 3
        private const val ENCRYPTED_PREFIX = "enc:v1:"
        private const val GLOBAL_WIDGET_CACHE_KEY = "ops_widget_snapshot"
    }
}
