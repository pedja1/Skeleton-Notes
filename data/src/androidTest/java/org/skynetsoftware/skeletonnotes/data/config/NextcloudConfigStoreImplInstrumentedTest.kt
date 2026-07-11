package org.skynetsoftware.skeletonnotes.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [NextcloudConfigStoreImpl] that exercise the real Android Keystore-backed
 * encryption of the Nextcloud app password.
 */
@RunWith(AndroidJUnit4::class)
class NextcloudConfigStoreImplInstrumentedTest {
    private lateinit var prefs: SharedPreferences

    private companion object {
        const val PREFS_NAME = "nextcloud"
        const val KEY_APP_PASSWORD = "appPassword"
        const val KEY_APP_PASSWORD_ENCRYPTED = "appPasswordEnc"
        const val PLAINTEXT_PASSWORD = "super-secret-token"
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun setServerConfigStoresEncryptedNotPlaintextPassword() {
        val store = NextcloudConfigStoreImpl(prefs)

        store.setServerConfig("https://cloud.example.com", "user", PLAINTEXT_PASSWORD)

        // The raw persisted value must be ciphertext, and no cleartext copy may remain.
        val stored = prefs.getString(KEY_APP_PASSWORD_ENCRYPTED, null)
        assertTrue(stored != null && stored.isNotBlank())
        assertNotEquals(PLAINTEXT_PASSWORD, stored)
        assertFalse(prefs.contains(KEY_APP_PASSWORD))
        // The in-memory value is still the decrypted plaintext.
        assertEquals(PLAINTEXT_PASSWORD, store.appPassword.value)
    }

    @Test
    fun passwordDecryptsAcrossNewStoreInstance() {
        NextcloudConfigStoreImpl(prefs)
            .setServerConfig("https://cloud.example.com", "user", PLAINTEXT_PASSWORD)

        // A fresh instance reads and decrypts the persisted ciphertext.
        val reopened = NextcloudConfigStoreImpl(prefs)
        assertEquals(PLAINTEXT_PASSWORD, reopened.appPassword.value)
    }

    @Test
    fun legacyPlaintextPasswordIsMigratedToEncrypted() {
        // Simulate a config written before encryption existed.
        prefs.edit().putString(KEY_APP_PASSWORD, PLAINTEXT_PASSWORD).commit()

        val store = NextcloudConfigStoreImpl(prefs)

        assertEquals(PLAINTEXT_PASSWORD, store.appPassword.value)
        // Migration removes the plaintext and writes the encrypted form.
        assertFalse(prefs.contains(KEY_APP_PASSWORD))
        val stored = prefs.getString(KEY_APP_PASSWORD_ENCRYPTED, null)
        assertTrue(stored != null && stored.isNotBlank())
        assertNotEquals(PLAINTEXT_PASSWORD, stored)
    }

    @Test
    fun clearServerConfigRemovesStoredPassword() {
        val store = NextcloudConfigStoreImpl(prefs)
        store.setServerConfig("https://cloud.example.com", "user", PLAINTEXT_PASSWORD)

        store.clearServerConfig()

        assertNull(store.appPassword.value)
        assertFalse(prefs.contains(KEY_APP_PASSWORD))
        assertFalse(prefs.contains(KEY_APP_PASSWORD_ENCRYPTED))
    }
}
