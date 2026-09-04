package com.cashbuddy.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "cashbuddy_master_key"
        private const val PREFS_NAME = "cashbuddy_keystore_vault"
        private const val KEY_ENCRYPTED_PASSPHRASE = "enc_db_passphrase"
        private const val KEY_GCM_IV = "enc_db_iv"
        private const val GCM_TAG_LENGTH = 128
        private const val PASSPHRASE_BYTE_LENGTH = 32
    }

    private val lock = Any()

    /**
     * Retrieves the decrypted 32-byte database encryption passphrase,
     * or generates a new cryptographically random passphrase and encrypts it
     * into the hardware-backed vault if not yet created.
     */
    fun getOrCreateDatabasePassphrase(): ByteArray = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedBase64 = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)
        val ivBase64 = prefs.getString(KEY_GCM_IV, null)

        if (encryptedBase64 != null && ivBase64 != null) {
            try {
                val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                return decryptPassphrase(encryptedBytes, iv)
            } catch (e: Exception) {
                // If hardware key became invalid (e.g. OS upgrade / key wipe), fall through to generate fresh
            }
        }

        // Generate cryptographically secure random 32-byte passphrase
        val freshPassphrase = ByteArray(PASSPHRASE_BYTE_LENGTH)
        SecureRandom().nextBytes(freshPassphrase)

        // Encrypt with Android Keystore hardware key
        val (ciphertext, iv) = encryptPassphrase(freshPassphrase)
        prefs.edit()
            .putString(KEY_ENCRYPTED_PASSPHRASE, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_GCM_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .commit()

        return freshPassphrase
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        return generateMasterKey(preferStrongBox = true)
    }

    private fun generateMasterKey(preferStrongBox: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val hasStrongBox = preferStrongBox &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        val builder = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (hasStrongBox) {
            try {
                builder.setIsStrongBoxBacked(true)
                keyGenerator.init(builder.build())
                return keyGenerator.generateKey()
            } catch (e: Exception) {
                // StrongBox not available on this specific SoC/hardware, fallback to standard TEE
                return generateMasterKey(preferStrongBox = false)
            }
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun encryptPassphrase(plainPassphrase: ByteArray): Pair<ByteArray, ByteArray> {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainPassphrase)
        return Pair(ciphertext, iv)
    }

    private fun decryptPassphrase(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Basic root detection heuristics (zero network, local checks only).
     */
    fun isDeviceRooted(): Boolean {
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }

        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        return paths.any { java.io.File(it).exists() }
    }
}
