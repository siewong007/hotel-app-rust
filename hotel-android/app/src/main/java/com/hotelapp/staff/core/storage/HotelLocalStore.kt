package com.hotelapp.staff.core.storage

import kotlinx.serialization.Serializable

@Serializable
data class LocalDataScope(
    val serverOrigin: String = "",
    val userId: Long = 0L,
)

@Serializable
data class StoredCacheEntry(
    val cacheKey: String,
    val payloadJson: String,
    val updatedAtMs: Long,
)

@Serializable
data class StoredPendingMutation(
    val id: String,
    val kind: String,
    val targetId: Long,
    val payloadJson: String,
    val createdAtMs: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val serverOrigin: String = "",
    val userId: Long = 0L,
    val sessionGeneration: Long = 0L,
)

interface HotelLocalStore {
    suspend fun putCache(key: String, payloadJson: String, nowMs: Long = System.currentTimeMillis())
    suspend fun getCache(key: String): StoredCacheEntry?
    suspend fun enqueueMutation(mutation: StoredPendingMutation)
    suspend fun listPendingMutations(): List<StoredPendingMutation>
    suspend fun deleteMutation(id: String)
    suspend fun recordMutationFailure(id: String, error: String)
    suspend fun purgeOtherScopes(activeOrigin: String, activeUserId: Long) {}
    suspend fun clearAll()
}
