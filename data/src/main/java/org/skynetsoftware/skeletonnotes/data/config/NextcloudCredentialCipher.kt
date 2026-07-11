package org.skynetsoftware.skeletonnotes.data.config

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts and decrypts the Nextcloud app password using an AES-256-GCM key held in the
 * Android Keystore, so the credential is never persisted in cleartext. The key material never
 * leaves the Keystore; only ciphertext (IV + encrypted bytes, Base64-encoded) is stored in
 * [android.content.SharedPreferences].
 */
internal class NextcloudCredentialCipher {
    companion object {
        private const val TAG = "NextcloudCipher"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "skeleton_notes_nextcloud_credential"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
    }

    /**
     * Encrypts [plaintext] and returns a Base64 string containing the GCM IV followed by the
     * ciphertext, or `null` if encryption fails.
     */
    fun encrypt(plaintext: String): String? =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
        } catch (t: GeneralSecurityException) {
            Log.e(TAG, "encrypt error", t)
            null
        } catch (t: IOException) {
            Log.e(TAG, "encrypt error", t)
            null
        }

    /**
     * Decrypts a Base64 string produced by [encrypt], returning the original plaintext, or `null`
     * if decryption fails (for example if the Keystore key was invalidated or the data is corrupt).
     */
    fun decrypt(stored: String): String? {
        return try {
            val combined = Base64.decode(stored, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH_BYTES) return null
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val encrypted = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (t: GeneralSecurityException) {
            Log.e(TAG, "decrypt error", t)
            null
        } catch (t: IOException) {
            Log.e(TAG, "decrypt error", t)
            null
        } catch (t: IllegalArgumentException) {
            Log.e(TAG, "decrypt error", t)
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec =
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
