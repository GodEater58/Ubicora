package com.officinastock.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 4.11: small secrets (the team password) sealed with a key kept in the Android Keystore.
 * The key never leaves the phone's secure hardware, so a copy of the database file — or a backup
 * restored on another phone — does not reveal the secret: there it has to be typed again.
 *
 * Where the Keystore is not available (unit tests, very unusual devices) values stay readable,
 * exactly as before 4.11; values written before 4.11 are read as they are and sealed on next write.
 */
object DeviceSecrets {
    private const val ALIAS = "ubicora_device_secrets_v1"
    private const val PREFIX = "ks1:"
    private val enc = Base64.getEncoder()
    private val dec = Base64.getDecoder()

    private fun key(): SecretKey? = runCatching {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
            generateKey()
        }
    }.getOrNull()

    /** True when [value] is sealed (as opposed to a legacy or fallback plain value). */
    fun isSealed(value: String) = value.startsWith(PREFIX)

    fun seal(plain: String): String {
        val k = key() ?: return plain
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, k) }
            val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + enc.encodeToString(cipher.iv) + ":" + enc.encodeToString(sealed)
        }.getOrElse { plain }
    }

    /** The plain value, or null when it was sealed on another phone (or the key was lost). */
    fun open(stored: String): String? {
        if (!isSealed(stored)) return stored
        val parts = stored.removePrefix(PREFIX).split(':')
        if (parts.size != 2) return null
        val k = key() ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, dec.decode(parts[0])))
            }
            String(cipher.doFinal(dec.decode(parts[1])), Charsets.UTF_8)
        }.getOrNull()
    }
}
