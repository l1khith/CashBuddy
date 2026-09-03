---
name: android-security-keystore
description: >-
  Use this skill when implementing hardware-backed encryption keys with Android Keystore (TEE/StrongBox),
  BiometricPrompt with BIOMETRIC_STRONG, root detection heuristics, and FLAG_SECURE window protection.
---

# Android Security & Keystore Procedures

## Overview
PaisaPal achieves hardware-backed security without cloud keys. Master database encryption keys are generated inside the Android Keystore (backed by TEE or StrongBox hardware).

## Key Generation & Storage

1. **Hardware Master Key (`AndroidKeystoreManager`)**:
   - Provider: `"AndroidKeyStore"`
   - Alias: `"paisapal_master_key"`
   - Algorithm: `KeyProperties.KEY_ALGORITHM_AES`
   - Purpose: `KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT`
   - Block Mode: `KeyProperties.BLOCK_MODE_GCM`
   - Padding: `KeyProperties.ENCRYPTION_PADDING_NONE`
   - Key Size: 256 bits.
   - Use `setIsStrongBoxBacked(true)` when `PackageManager.FEATURE_STRONGBOX_KEYSTORE` is available.

2. **Database Passphrase Vault**:
   - Generate a cryptographically secure 32-byte random passphrase using `SecureRandom`.
   - Encrypt the random passphrase using the hardware master key with GCM mode.
   - Store the ciphertext and initialization vector (IV) in private `EncryptedSharedPreferences` or app-private storage.
   - On cold start, decrypt the passphrase using the Keystore key to unlock the SQLCipher driver.

## Biometric Security (`AndroidBiometricAuth`)

- Use `androidx.biometric.BiometricPrompt`.
- Configure `setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)`.
- Require biometric unlock on application cold launch.
- Trigger automatic lock if the app has been in the background for longer than 5 minutes (`300,000` ms).

## Screen & Memory Protection

- Call `window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)` on `MainActivity` for sensitive transaction details and settings.
- Run root detection checks on cold start (`su` binary search, `test-keys` build tags, dangerous system properties).

## Verification Checklist
- [ ] Attempting to open the `paisapal.db` file with a standard unencrypted SQLite reader fails with `file is not a database`.
- [ ] Biometric prompt triggers when returning from background after 5 minutes.
- [ ] Screenshots are prevented when `FLAG_SECURE` is set.
