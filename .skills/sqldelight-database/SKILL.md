---
name: sqldelight-database
description: >-
  Use this skill when defining or modifying SQLDelight 2.2.1 schemas (.sq files), triggers, indices,
  custom column adapters, and integrating SQLCipher AES-256 GCM encryption via expect/actual DatabaseDriverFactory.
---

# SQLDelight Database & Encryption Guidelines

## Overview
PaisaPal stores all financial records locally in an encrypted SQLite database using SQLDelight 2.2.1 and SQLCipher 4.6.1 with AES-256 GCM.

## Schema Conventions (`shared/src/commonMain/sqldelight/com/paisapal/db/`)

1. **Table Structure**:
   - Every primary key is `INTEGER PRIMARY KEY AUTOINCREMENT`.
   - Timestamps are stored as milliseconds since epoch typed with `INTEGER AS Long`.
   - Flags are typed with `INTEGER AS Boolean` (`1 = true`, `0 = false`).
   - Floats/amounts are stored as `REAL` (mapped to Kotlin `Double`).

2. **Required Tables**:
   - `accounts.sq`: Stores bank accounts, credit cards, cash, and digital wallets.
   - `categories.sq`: System default and user-defined income/expense categories.
   - `transactions.sq`: Complete ledger with balance triggers and foreign keys.
   - `merchant_rules.sq`: User pattern matching rules for automated category assignment.
   - `budgets.sq`: Category spending budgets and alert thresholds.
   - `goals.sq`: Savings targets and deadline metrics.
   - `settings.sq`: Key-value application state.

3. **Balance Update Triggers**:
   - Transactions automatically update the linked account balance upon insertion:
   ```sql
   CREATE TRIGGER update_account_balance_debit
   AFTER INSERT ON transactions
   WHEN NEW.type = 'DEBIT' AND NEW.status = 'CONFIRMED' AND NEW.account_id IS NOT NULL
   BEGIN
       UPDATE accounts SET balance = balance - NEW.amount, updated_at = NEW.created_at WHERE id = NEW.account_id;
   END;

   CREATE TRIGGER update_account_balance_credit
   AFTER INSERT ON transactions
   WHEN NEW.type = 'CREDIT' AND NEW.status = 'CONFIRMED' AND NEW.account_id IS NOT NULL
   BEGIN
       UPDATE accounts SET balance = balance + NEW.amount, updated_at = NEW.created_at WHERE id = NEW.account_id;
   END;
   ```

4. **Encrypted Driver Factory Pattern**:
   - `expect class DatabaseDriverFactory { fun createDriver(passphrase: ByteArray): SqlDriver }`
   - In `androidMain`: Use `AndroidSqliteDriver` wrapping SQLCipher `SupportFactory`.
   - In `iosMain`: Use `NativeSqliteDriver`.

5. **Reactive Queries**:
   - Always expose lists using SQLDelight's coroutine extensions:
   ```kotlin
   queries.getAll()
       .asFlow()
       .mapToList(dispatcher)
   ```

## Verification Steps
- Run `./gradlew :shared:generateSqlDelightInterface` to verify valid syntax.
- Confirm all foreign keys and triggers compile cleanly without circular locks.
