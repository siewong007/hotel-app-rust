package com.hotelapp.staff.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.File

interface CookieStorage {
    fun read(): ByteArray?
    fun write(data: ByteArray)
    fun clear()
}

class FileCookieStorage(private val file: File) : CookieStorage {
    override fun read(): ByteArray? = if (file.exists()) file.readBytes() else null

    override fun write(data: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(file)) {
            file.writeBytes(data)
            tmp.delete()
        }
    }

    override fun clear() {
        if (file.exists()) file.delete()
    }
}

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
    val persistent: Boolean,
) {
    fun key(): String = "$name|$domain|$path|$hostOnly"

    fun toOkHttp(): Cookie {
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .path(path)
        if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()
        return builder.build()
    }

    companion object {
        fun fromOkHttp(cookie: Cookie): StoredCookie = StoredCookie(
            name = cookie.name,
            value = cookie.value,
            expiresAt = cookie.expiresAt,
            domain = cookie.domain,
            path = cookie.path,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            hostOnly = cookie.hostOnly,
            persistent = cookie.persistent,
        )
    }
}

class EncryptedCookieJar(
    private val storage: CookieStorage,
    private val cipher: SecretCipher,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) : CookieJar {

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var cache: MutableList<StoredCookie> = mutableListOf()

    init {
        loadFromDisk()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val now = clockMillis()
        synchronized(lock) {
            val incoming = cookies.map { StoredCookie.fromOkHttp(it) }
            val incomingKeys = incoming.mapTo(HashSet()) { it.key() }
            cache.removeAll { it.key() in incomingKeys || it.expiresAt <= now }
            incoming.filterTo(cache) { it.expiresAt > now && it.value.isNotEmpty() }
            persistLocked()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = clockMillis()
        synchronized(lock) {
            val expiredRemoved = cache.removeAll { it.expiresAt <= now }
            if (expiredRemoved) {
                persistLocked()
            }
            return cache
                .asSequence()
                .map { it.toOkHttp() }
                .filter { it.matches(url) }
                .toList()
        }
    }

    fun clearForHost(host: String) {
        synchronized(lock) {
            val normalized = host.lowercase()
            val changed = cache.removeAll {
                it.domain.lowercase() == normalized ||
                    (!it.hostOnly && normalized.endsWith(".${it.domain.lowercase()}"))
            }
            if (changed) persistLocked()
        }
    }

    fun clearAll() {
        synchronized(lock) {
            cache.clear()
            storage.clear()
        }
    }

    private fun loadFromDisk() {
        synchronized(lock) {
            val raw = storage.read() ?: return
            val decrypted = runCatching { cipher.decrypt(raw) }.getOrNull()
            if (decrypted == null) {
                storage.clear()
                return
            }
            val decoded = runCatching {
                json.decodeFromString<List<StoredCookie>>(decrypted.decodeToString())
            }.getOrNull()
            if (decoded == null) {
                storage.clear()
                return
            }
            val now = clockMillis()
            cache = decoded.filter { it.expiresAt > now }.toMutableList()
        }
    }

    private fun persistLocked() {
        val persistentCookies = cache.filter { it.persistent }
        if (persistentCookies.isEmpty()) {
            storage.clear()
            return
        }
        val encoded = json.encodeToString(persistentCookies).encodeToByteArray()
        storage.write(cipher.encrypt(encoded))
    }
}
