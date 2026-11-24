package com.kinzer.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.credentialVaultDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pipulse_credential_vault"
)

/**
 * Hardware-backed Android Keystore (AES-256-GCM) vault for storing Pi-hole passwords and API tokens
 * locally per device ID so sensitive LAN/VPN credentials are never saved in plaintext or uploaded
 * to cloud storage.
 */
class DeviceCredentialVault(private val appContext: Context) {

    private val keyAlias = "pipulse_device_secret_aes_key_v1"

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun prefKeyFor(deviceId: String): Preferences.Key<String> =
        stringPreferencesKey("cred_$deviceId")

    suspend fun saveSecret(deviceId: String, plainSecret: String) {
        if (deviceId.isBlank()) return
        if (plainSecret.isEmpty()) {
            removeSecret(deviceId)
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainSecret.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)

            appContext.credentialVaultDataStore.edit { prefs ->
                prefs[prefKeyFor(deviceId)] = encoded
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt credential for device $deviceId", e)
        }
    }

    suspend fun getSecret(deviceId: String): String {
        if (deviceId.isBlank()) return ""
        return try {
            val encoded = appContext.credentialVaultDataStore.data.first()[prefKeyFor(deviceId)]
                ?: return ""
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < 2) return ""
            val ivLen = combined[0].toInt() and 0xFF
            if (combined.size <= 1 + ivLen) return ""
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val cipherBytes = combined.copyOfRange(1 + ivLen, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decrypt credential for device $deviceId: ${e.message}")
            ""
        }
    }

    suspend fun removeSecret(deviceId: String) {
        if (deviceId.isBlank()) return
        appContext.credentialVaultDataStore.edit { prefs ->
            prefs.remove(prefKeyFor(deviceId))
        }
    }

    suspend fun saveSshPrivateKey(keyPem: String) = saveSecret(SSH_PRIVATE_KEY_SLOT, keyPem)

    suspend fun getSshPrivateKey(): String = getSecret(SSH_PRIVATE_KEY_SLOT)

    suspend fun removeSshPrivateKey() = removeSecret(SSH_PRIVATE_KEY_SLOT)

    suspend fun saveSshPassphrase(passphrase: String) = saveSecret(SSH_PASSPHRASE_SLOT, passphrase)

    suspend fun getSshPassphrase(): String = getSecret(SSH_PASSPHRASE_SLOT)

    suspend fun removeSshPassphrase() = removeSecret(SSH_PASSPHRASE_SLOT)

    companion object {
        private const val TAG = "DeviceCredentialVault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val SSH_PRIVATE_KEY_SLOT = "__ssh_fleet_private_key__"
        private const val SSH_PASSPHRASE_SLOT = "__ssh_fleet_passphrase__"
    }
}
