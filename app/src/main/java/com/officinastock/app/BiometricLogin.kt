package com.officinastock.app

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 4.21.1: sign in with the fingerprint instead of the PIN, profile by profile, on this phone only.
 *
 * Turning it on creates a key in the Android Keystore that can be used only right after a strong
 * biometric check and that Android deletes when a new fingerprint is enrolled. A random secret is
 * sealed with it; signing in means opening that secret again, so a fingerprint the phone did not
 * verify can never sign in. The PIN keeps working and stays the way back (after 5 wrong fingerprints,
 * after a new fingerprint is added, on another phone).
 */
object BiometricLogin {
    private const val PREFS = "ubicora_biometric"
    private const val KEYSTORE = "AndroidKeyStore"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun alias(profileId: Long) = "ubicora_fingerprint_$profileId"
    private val enc = Base64.getEncoder(); private val dec = Base64.getDecoder()

    /** The phone has a fingerprint (or another strong biometric) enrolled and usable now. */
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    fun enabled(context: Context, profileId: Long): Boolean = prefs(context).contains("p$profileId")

    fun disable(context: Context, profileId: Long) {
        prefs(context).edit().remove("p$profileId").apply()
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(alias(profileId)) }
    }

    private fun newKey(profileId: Long): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias(profileId), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(true)
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG) }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }
    private fun key(profileId: Long): SecretKey? =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.getKey(alias(profileId), null) as? SecretKey

    /** Turns the fingerprint on for [profileId]: asks for it once, then remembers it. [done] gets null or the reason it failed. */
    fun enable(activity: FragmentActivity, profileId: Long, profileName: String, done: (String?) -> Unit) {
        val cipher = runCatching { Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, newKey(profileId)) } }
            .getOrElse { done("Impronta non disponibile su questo telefono"); return }
        prompt(activity, cipher, "Attiva l'impronta", "Per accedere a $profileName", "Annulla", { c ->
            val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val sealed = c.doFinal(secret)
            prefs(activity).edit().putString("p$profileId", enc.encodeToString(c.iv) + ":" + enc.encodeToString(sealed)).apply()
            done(null)
        }, { error -> disable(activity, profileId); done(error) })
    }

    /**
     * Signs in [profileId] with the fingerprint. [usePin]: the person chose the PIN, or the fingerprint
     * can no longer be used (then [reason] says why and the fingerprint is turned off).
     */
    fun unlock(activity: FragmentActivity, profileId: Long, profileName: String, onSuccess: () -> Unit, usePin: (String?) -> Unit) {
        val stored = prefs(activity).getString("p$profileId", null)?.split(':')
        val cipher = runCatching {
            require(stored != null && stored.size == 2)
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, requireNotNull(key(profileId)), GCMParameterSpec(128, dec.decode(stored[0])))
            }
        }.getOrElse { e ->
            disable(activity, profileId)
            usePin(if (e is KeyPermanentlyInvalidatedException) "Le impronte del telefono sono cambiate: accedi con il PIN e riattiva l'impronta"
                else "Impronta non più disponibile: accedi con il PIN")
            return
        }
        prompt(activity, cipher, "Accedi a $profileName", "Appoggia il dito sul sensore", "Usa il PIN", { c ->
            c.doFinal(dec.decode(stored!![1]))   // throws unless this very fingerprint check unlocked the key
            onSuccess()
        }, { error -> usePin(error) })
    }

    private fun prompt(activity: FragmentActivity, cipher: Cipher, title: String, subtitle: String, negative: String,
                       success: (Cipher) -> Unit, failure: (String?) -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (c == null) { failure("Impronta non verificata"); return }
                runCatching { success(c) }.onFailure { failure("Impronta non verificata") }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Cancelled or "Usa il PIN": no message, the PIN field opens. Anything else is said.
                val quiet = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                failure(if (quiet) null else errString.toString())
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle)
            .setNegativeButtonText(negative).setAllowedAuthenticators(BIOMETRIC_STRONG).build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}

/** The activity behind a Compose context (dialogs wrap it). */
tailrec fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.fragmentActivity()
    else -> null
}
