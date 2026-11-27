package com.hotelapp.staff.core.network

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(cipherText: ByteArray): ByteArray
}

class AndroidKeystoreCipher(
    private val keyAlias: String = "com.hotelapp.staff.cookie_key",
) : SecretCipher {

    private val keyLock = Any()

    override fun encrypt(plain: ByteArray): ByteArray = synchronized(keyLock) {
        return try {
            encryptWithKey(plain, getOrCreateKey())
        } catch (ex: GeneralSecurityException) {
            if (isPermanentKeyCorruption(ex)) {
                resetKey()
                encryptWithKey(plain, getOrCreateKey())
            } else {
                throw ex
            }
        }
    }

    override fun decrypt(cipherText: ByteArray): ByteArray {
        if (cipherText.size < 2) error("Corrupt encrypted payload")
        val ivLen = cipherText[0].toInt() and 0xFF
        if (cipherText.size <= 1 + ivLen) error("Truncated encrypted payload")
        val iv = cipherText.copyOfRange(1, 1 + ivLen)
        val encrypted = cipherText.copyOfRange(1 + ivLen, cipherText.size)
        return synchronized(keyLock) {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.doFinal(encrypted)
            } catch (ex: GeneralSecurityException) {
                if (isPermanentKeyCorruption(ex)) {
                    resetKey()
                }
                throw ex
            } catch (ex: ProviderException) {
                // Keep the Keystore entry intact across transient KeyMint/TEE availability errors.
                throw ex
            }
        }
    }

    private fun isPermanentKeyCorruption(ex: Throwable): Boolean =
        ex is KeyPermanentlyInvalidatedException ||
            ex is UnrecoverableKeyException ||
            ex is InvalidKeyException ||
            ex is InvalidAlgorithmParameterException

    private fun encryptWithKey(plain: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    private fun resetKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
