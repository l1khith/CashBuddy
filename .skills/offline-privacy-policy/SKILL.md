---
name: offline-privacy-policy
description: >-
  Use this skill when implementing, auditing, or verifying PaisaPal's 100% offline data policy,
  zero network and zero SMS permission boundaries, Scoped Storage data export (CSV/Excel), and Play Store compliance.
---

# Offline Privacy Policy & Data Portability Procedures

## Overview
PaisaPal guarantees total financial sovereignty. All processing is on-device, and data export or backup is user-initiated and local-only.

## Permission Boundary Rules

1. **Manifest Enforcement**:
   - `AndroidManifest.xml` must NOT declare `android.permission.INTERNET`.
   - `AndroidManifest.xml` must NOT declare `android.permission.READ_SMS`.
   - `AndroidManifest.xml` must NOT declare `android.permission.RECEIVE_SMS`.
   - Any commit introducing network libraries (e.g. Ktor Client, OkHttp, Retrofit) must be immediately rejected.

2. **Scoped Storage Export (`FileExporter`)**:
   - In `androidMain`: Use Android MediaStore API or `Intent.ACTION_CREATE_DOCUMENT` to allow the user to select the destination folder.
   - Never write to legacy shared public directories without Storage Access Framework (SAF).
   - Export formats supported:
     - Standard CSV (comma-separated, UTF-8, RFC 4180 compliant).
     - Formatted Excel (tab-delimited or lightweight XML/CSV workbook).

3. **Encrypted Database Backup & Restore**:
   - Backup: Encrypt raw SQLite export with user password via PBKDF2 + AES-256 GCM.
   - Output single encrypted `.paisabackup` file.
   - Restore: Decrypt with user password and validate SQLite schema header before replacing current active database.

## Verification Checklist
- [ ] Run `grep -ri "android.permission.INTERNET" androidApp/src/main/AndroidManifest.xml` $\to$ must return 0 results.
- [ ] Run `grep -ri "android.permission.READ_SMS" androidApp/src/main/AndroidManifest.xml` $\to$ must return 0 results.
- [ ] Exporting a 1,000-transaction CSV file succeeds on Android 10+ (API 29+) without requiring legacy storage permissions.
