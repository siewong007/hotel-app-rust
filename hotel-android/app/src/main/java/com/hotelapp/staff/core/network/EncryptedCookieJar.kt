package com.hotelapp.staff.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.File
import java.security.ProviderException
import java.util.concurrent.atomic.AtomicLong

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
        val tmp = File(file.parentFile, "${file.name}.tmp")
        if (tmp.exists()) tmp.delete()
    }
}

class CookieEpochInterceptor(
    private val cookieJar: EncryptedCookieJar,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        cookieJar.withCallEpoch { chain.proceed(chain.request()) }
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
    private val clearEpoch = AtomicLong(0L)
    private val activeCallEpoch = ThreadLocal<Long?>()
    private var cache: MutableList<StoredCookie> = mutableListOf()
    private var deferredTransientLoad: Boolean = false

    init {
        loadFromDisk()
    }

    fun <T> withCallEpoch(block: () -> T): T {
        val previous = activeCallEpoch.get()
        activeCallEpoch.set(clearEpoch.get())
        return try {
            block()
        } finally {
            if (previous == null) activeCallEpoch.remove() else activeCallEpoch.set(previous)
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val now = clockMillis()
        synchronized(lock) {
            val expectedEpoch = activeCallEpoch.get()
            if (expectedEpoch != null && expectedEpoch != clearEpoch.get()) {
                return
            }
            retryDeferredLoadLocked()
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
            retryDeferredLoadLocked()
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
            clearEpoch.incrementAndGet()
            retryDeferredLoadLocked()
            val normalized = host.lowercase()
            val changed = cache.removeAll {
                it.domain.lowercase() == normalized ||
                    (!it.hostOnly && normalized.endsWith(".${it.domain.lowercase()}"))
            }
            if (changed) persistLocked()
        }
    }

    fun hasStoredCookies(): Boolean {
        val now = clockMillis()
        synchronized(lock) {
            retryDeferredLoadLocked()
            return cache.any { it.expiresAt > now && it.value.isNotEmpty() }
        }
    }

    fun hasRefreshCookieFor(baseUrl: String): Boolean {
        val parsed = baseUrl.trim().toHttpUrlOrNull() ?: return false
        val refreshUrl = parsed.newBuilder()
            .encodedPath("${parsed.encodedPath.trimEnd('/')}/api/auth/refresh")
            .build()
        val now = clockMillis()
        synchronized(lock) {
            retryDeferredLoadLocked()
            return cache.any {
                it.name == "refresh_token" &&
                    it.expiresAt > now &&
                    it.value.isNotEmpty() &&
                    it.toOkHttp().matches(refreshUrl)
            }
        }
    }

    fun clearAll() {
        synchronized(lock) {
            clearEpoch.incrementAndGet()
            deferredTransientLoad = false
            cache.clear()
            storage.clear()
        }
    }

    private fun retryDeferredLoadLocked() {
        if (deferredTransientLoad) {
            loadFromDiskLocked()
        }
    }

    private fun loadFromDisk() {
        synchronized(lock) {
            loadFromDiskLocked()
        }
    }

    private fun loadFromDiskLocked() {
        val raw = storage.read() ?: run {
            deferredTransientLoad = false
            return
        }
        val decrypted = try {
            cipher.decrypt(raw)
        } catch (_: ProviderException) {
            // Transient TEE/Keystore availability issue: preserve file on disk to retry later.
            deferredTransientLoad = true
            return
        } catch (_: Throwable) {
            deferredTransientLoad = false
            storage.clear()
            return
        }
        val decoded = runCatching {
            json.decodeFromString<List<StoredCookie>>(decrypted.decodeToString())
        }.getOrNull()
        if (decoded == null) {
            deferredTransientLoad = false
            storage.clear()
            return
        }
        deferredTransientLoad = false
        val now = clockMillis()
        cache = decoded.filter { it.expiresAt > now }.toMutableList()
    }

    private fun persistLocked() {
        val persistentCookies = cache.filter { it.persistent }
        if (persistentCookies.isEmpty()) {
            storage.clear()
            return
        }
        val encoded = json.encodeToString(persistentCookies).encodeToByteArray()
        runCatching {
            storage.write(cipher.encrypt(encoded))
        }.onFailure {
            storage.clear()
        }
    }
}
