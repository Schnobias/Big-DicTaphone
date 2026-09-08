package com.bigdictaphone.app.services

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypt preference values with a non-exportable Android Keystore key. */
class SecretStore {
    fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return PREFIX + Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun decrypt(value: String): String {
        if (!value.startsWith(PREFIX)) return value // Read legacy values until migration completes.
        return runCatching {
            val data = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
            require(data.size >= 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            cipher.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8)
        }.getOrDefault("") // A lost key requires re-entering credentials, never a plaintext fallback.
    }
    companion object {
        const val PREFIX = "encrypted:v1:"
        @Synchronized private fun key(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey("bigdictaphone-secrets-v1", null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder("bigdictaphone-secrets-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
        }
    }
}
