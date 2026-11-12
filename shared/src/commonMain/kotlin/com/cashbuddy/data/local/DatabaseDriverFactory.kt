// NO-NETWORK
package com.cashbuddy.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.cashbuddy.db.AppDatabase

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driver: SqlDriver): AppDatabase {
    // Enable performance optimizations for SQLite & SQLCipher
    executeQuietly(driver, "PRAGMA journal_mode = WAL;")
    executeQuietly(driver, "PRAGMA synchronous = NORMAL;")
    executeQuietly(driver, "PRAGMA foreign_keys = ON;")
    executeQuietly(driver, "PRAGMA temp_store = MEMORY;")
    executeQuietly(driver, "PRAGMA cache_size = -8000;") // 8 MB cache
    verifyPragmas(driver)
    ensureSchema(driver)
    return AppDatabase(driver)
}

/**
 * Audit fix 1.6: silently-failed pragmas are worse than no pragmas (e.g. FK enforcement
 * silently off = cascade/invalid-reference guarantees void). Read the live values back
 * and log loudly when reality doesn't match intent.
 */
private fun verifyPragmas(driver: SqlDriver) {
    try {
        val journalMode = driver.executeQuery(
            identifier = null,
            sql = "PRAGMA journal_mode;",
            mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
            parameters = 0
        ).value
        if (journalMode == null || !journalMode.equals("wal", ignoreCase = true)) {
            println("[DatabaseDriverFactory] WARNING: journal_mode=$journalMode (expected wal)")
        }
    } catch (e: Throwable) {
        println("[DatabaseDriverFactory] WARNING: could not read journal_mode: ${e.message}")
    }
    try {
        val foreignKeys = driver.executeQuery(
            identifier = null,
            sql = "PRAGMA foreign_keys;",
            mapper = { cursor ->
                val value = if (cursor.next().value) cursor.getLong(0) else null
                QueryResult.Value(value)
            },
            parameters = 0
        ).value
        if (foreignKeys != 1L) {
            println("[DatabaseDriverFactory] WARNING: foreign_keys=$foreignKeys (expected 1) — referential enforcement may be OFF")
        }
    } catch (e: Throwable) {
        println("[DatabaseDriverFactory] WARNING: could not read foreign_keys: ${e.message}")
    }
}

private fun executeDdl(driver: SqlDriver, sql: String, logError: Boolean = true) {
    try {
        driver.execute(null, sql, 0)
    } catch (e: Throwable) {
        if (logError) {
            println("[DatabaseDriverFactory] DDL notice: ${e.message}")
        }
    }
}

private fun executeQuietly(driver: SqlDriver, sql: String) {
    executeDdl(driver, sql, logError = false)
}

fun ensureSchema(driver: SqlDriver) {
    // Fast path: if critical tables are already present, avoid re-executing DDL operations
    val alreadyCreated = try {
        driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('transactions', 'debug_log', 'accounts', 'budgets', 'categories', 'merchant_rules', 'recurring_pattern', 'merge_log');",
            mapper = { cursor ->
                val count = if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L
                QueryResult.Value(count >= 8L)
            },
            parameters = 0
        ).value
    } catch (_: Throwable) {
        false
    }

    if (alreadyCreated) {
        runMigrations(driver)
        return
    }
    val statements = listOf(
        """
        CREATE TABLE IF NOT EXISTS debug_log (
            id TEXT PRIMARY KEY NOT NULL,
            timestamp INTEGER NOT NULL,
            source_type TEXT NOT NULL,
            package_name TEXT,
            sender_id TEXT,
            raw_title TEXT,
            raw_text TEXT NOT NULL,
            raw_text_hash TEXT,
            detected_source TEXT,
            evidence_json TEXT,
            p_transaction REAL,
            contributions_json TEXT,
            field_confidences_json TEXT,
            policy_action TEXT,
            pipeline_outcome TEXT,
            resulting_tx_id TEXT,
            merge_target_id TEXT,
            error_message TEXT
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS accounts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            type TEXT NOT NULL CHECK(type IN ('BANK', 'WALLET', 'CASH', 'CREDIT_CARD', 'INVESTMENT')),
            number TEXT,
            bank TEXT,
            balance REAL NOT NULL DEFAULT 0.0,
            currency TEXT NOT NULL DEFAULT 'INR',
            is_active INTEGER NOT NULL DEFAULT 1,
            sort_order INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS budgets (
            id TEXT PRIMARY KEY NOT NULL,
            category TEXT NOT NULL,
            amount REAL NOT NULL,
            period TEXT NOT NULL,
            start_date INTEGER NOT NULL,
            is_active INTEGER NOT NULL DEFAULT 1,
            last_alert_state TEXT,
            last_alert_at INTEGER,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS categories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            type TEXT NOT NULL CHECK(type IN ('EXPENSE', 'INCOME')),
            icon TEXT NOT NULL DEFAULT 'category',
            color TEXT NOT NULL DEFAULT '#FF6B6B',
            is_default INTEGER NOT NULL DEFAULT 0,
            sort_order INTEGER NOT NULL DEFAULT 0,
            parent_id INTEGER,
            created_at INTEGER NOT NULL,
            FOREIGN KEY (parent_id) REFERENCES categories(id)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS goals (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            target_amount REAL NOT NULL,
            current_amount REAL NOT NULL DEFAULT 0.0,
            deadline INTEGER,
            category_id INTEGER,
            icon TEXT NOT NULL DEFAULT 'flag',
            color TEXT NOT NULL DEFAULT '#2ECC71',
            status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            FOREIGN KEY (category_id) REFERENCES categories(id)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS merchant_rules (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            pattern TEXT NOT NULL,
            category_id INTEGER NOT NULL,
            is_regex INTEGER NOT NULL DEFAULT 0,
            priority INTEGER NOT NULL DEFAULT 0,
            match_count INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            FOREIGN KEY (category_id) REFERENCES categories(id)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS raw_messages (
            id TEXT PRIMARY KEY NOT NULL,
            source_type TEXT NOT NULL,
            package_name TEXT,
            sender_id TEXT,
            title TEXT,
            text TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            processed_at INTEGER NOT NULL,
            resulting_tx_id TEXT
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS settings (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL,
            updated_at INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS signal_observations (
            signal TEXT PRIMARY KEY NOT NULL,
            true_positives INTEGER NOT NULL DEFAULT 0,
            false_positives INTEGER NOT NULL DEFAULT 0,
            true_negatives INTEGER NOT NULL DEFAULT 0,
            false_negatives INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS raw_training_data (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            raw_text TEXT NOT NULL,
            source TEXT NOT NULL,
            source_app TEXT,
            extracted_amount REAL,
            extracted_type TEXT,
            extracted_merchant TEXT,
            timestamp INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS user_corrections (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            merchant TEXT NOT NULL,
            source_app TEXT,
            raw_text TEXT,
            old_category TEXT,
            new_category TEXT NOT NULL,
            timestamp INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS transactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            amount REAL NOT NULL,
            type TEXT NOT NULL CHECK(type IN ('DEBIT', 'CREDIT')),
            currency TEXT NOT NULL DEFAULT 'INR',
            merchant TEXT NOT NULL,
            category_id INTEGER NOT NULL,
            account_id INTEGER,
            source_app TEXT NOT NULL,
            raw_text TEXT NOT NULL,
            confidence REAL NOT NULL DEFAULT 0.0,
            status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'MODIFIED')),
            notes TEXT,
            timestamp INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            is_merged INTEGER NOT NULL DEFAULT 0,
            merged_into_id INTEGER,
            payment_method TEXT NOT NULL DEFAULT 'UPI',
            is_loan_given INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (category_id) REFERENCES categories(id),
            FOREIGN KEY (account_id) REFERENCES accounts(id)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS merge_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            survivor_id INTEGER NOT NULL,
            merged_id INTEGER NOT NULL,
            merged_at INTEGER NOT NULL,
            FOREIGN KEY (survivor_id) REFERENCES transactions(id) ON DELETE CASCADE,
            FOREIGN KEY (merged_id) REFERENCES transactions(id) ON DELETE CASCADE
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS learned_token (
            token TEXT PRIMARY KEY NOT NULL,
            transaction_count INTEGER NOT NULL DEFAULT 0,
            non_transaction_count INTEGER NOT NULL DEFAULT 0,
            first_seen INTEGER NOT NULL,
            last_updated INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS recurring_pattern (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            merchant_normalized TEXT NOT NULL,
            amount_paise INTEGER NOT NULL,
            occurrence_count INTEGER NOT NULL,
            median_interval_hours REAL NOT NULL,
            cv REAL NOT NULL,
            period_hours INTEGER NOT NULL,
            autocorrelation_peak REAL NOT NULL,
            first_seen INTEGER NOT NULL,
            last_seen INTEGER NOT NULL,
            last_updated INTEGER NOT NULL,
            UNIQUE(merchant_normalized, amount_paise)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS ignored_packages (
            package_name TEXT PRIMARY KEY NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_debug_log_timestamp ON debug_log(timestamp DESC)",
        "CREATE INDEX IF NOT EXISTS idx_debug_log_policy ON debug_log(policy_action)",
        "CREATE INDEX IF NOT EXISTS idx_debug_log_package ON debug_log(package_name)",
        "CREATE INDEX IF NOT EXISTS idx_accounts_active ON accounts(is_active)",
        "CREATE INDEX IF NOT EXISTS idx_accounts_type ON accounts(type)",
        "CREATE INDEX IF NOT EXISTS idx_budgets_category ON budgets(category)",
        "CREATE INDEX IF NOT EXISTS idx_budgets_is_active ON budgets(is_active)",
        "CREATE INDEX IF NOT EXISTS idx_categories_type ON categories(type)",
        "CREATE INDEX IF NOT EXISTS idx_categories_parent ON categories(parent_id)",
        "CREATE INDEX IF NOT EXISTS idx_merchant_rules_pattern ON merchant_rules(pattern)",
        "CREATE INDEX IF NOT EXISTS idx_raw_timestamp ON raw_messages(timestamp DESC)",
        "CREATE INDEX IF NOT EXISTS idx_raw_training_data_timestamp ON raw_training_data(timestamp)",
        "CREATE INDEX IF NOT EXISTS idx_user_corrections_merchant ON user_corrections(merchant)",
        "CREATE INDEX IF NOT EXISTS idx_user_corrections_timestamp ON user_corrections(timestamp)",
        "CREATE INDEX IF NOT EXISTS idx_transactions_timestamp ON transactions(timestamp)",
        "CREATE INDEX IF NOT EXISTS idx_transactions_category ON transactions(category_id)",
        "CREATE INDEX IF NOT EXISTS idx_transactions_account ON transactions(account_id)",
        "CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status)",
        "CREATE INDEX IF NOT EXISTS idx_transactions_merchant ON transactions(merchant)",
        "CREATE INDEX IF NOT EXISTS idx_transactions_summary ON transactions(status, type, timestamp)",
        "CREATE INDEX IF NOT EXISTS idx_transactions_is_merged ON transactions(is_merged)",
        "CREATE INDEX IF NOT EXISTS idx_transactions_merged_into ON transactions(merged_into_id)",
        "CREATE INDEX IF NOT EXISTS idx_merge_log_survivor ON merge_log(survivor_id)",
        "CREATE INDEX IF NOT EXISTS idx_merge_log_merged ON merge_log(merged_id)",
        "CREATE INDEX IF NOT EXISTS idx_learned_token_counts ON learned_token(transaction_count, non_transaction_count)",
        "CREATE INDEX IF NOT EXISTS idx_pattern_merchant ON recurring_pattern(merchant_normalized)",
        "CREATE INDEX IF NOT EXISTS idx_pattern_amount ON recurring_pattern(amount_paise)",
        """
        CREATE TRIGGER IF NOT EXISTS update_account_balance_debit
        AFTER INSERT ON transactions
        WHEN new.type = 'DEBIT' AND new.status IN ('CONFIRMED', 'MODIFIED') AND new.account_id IS NOT NULL
        BEGIN
            UPDATE accounts SET balance = balance - new.amount, updated_at = new.created_at WHERE id = new.account_id;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS update_account_balance_credit
        AFTER INSERT ON transactions
        WHEN new.type = 'CREDIT' AND new.status IN ('CONFIRMED', 'MODIFIED') AND new.account_id IS NOT NULL
        BEGIN
            UPDATE accounts SET balance = balance + new.amount, updated_at = new.created_at WHERE id = new.account_id;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS update_account_balance_on_confirm_debit
        AFTER UPDATE OF status ON transactions
        WHEN old.status NOT IN ('CONFIRMED', 'MODIFIED') AND new.status IN ('CONFIRMED', 'MODIFIED') AND new.type = 'DEBIT' AND new.account_id IS NOT NULL
        BEGIN
            UPDATE accounts SET balance = balance - new.amount, updated_at = new.updated_at WHERE id = new.account_id;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS update_account_balance_on_confirm_credit
        AFTER UPDATE OF status ON transactions
        WHEN old.status NOT IN ('CONFIRMED', 'MODIFIED') AND new.status IN ('CONFIRMED', 'MODIFIED') AND new.type = 'CREDIT' AND new.account_id IS NOT NULL
        BEGIN
            UPDATE accounts SET balance = balance + new.amount, updated_at = new.updated_at WHERE id = new.account_id;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS revert_account_balance_on_unconfirm_debit
        AFTER UPDATE OF status ON transactions
        WHEN old.status IN ('CONFIRMED', 'MODIFIED') AND new.status NOT IN ('CONFIRMED', 'MODIFIED') AND old.type = 'DEBIT' AND old.account_id IS NOT NULL
        BEGIN
            UPDATE accounts SET balance = balance + old.amount, updated_at = new.updated_at WHERE id = old.account_id;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS revert_account_balance_on_unconfirm_credit
        AFTER UPDATE OF status ON transactions
        WHEN old.status IN ('CONFIRMED', 'MODIFIED') AND new.status NOT IN ('CONFIRMED', 'MODIFIED') AND old.type = 'CREDIT' AND old.account_id IS NOT NULL
        BEGIN
            UPDATE accounts SET balance = balance - old.amount, updated_at = new.updated_at WHERE id = old.account_id;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS revert_account_balance_on_delete_debit
        AFTER DELETE ON transactions
        WHEN old.status IN ('CONFIRMED', 'MODIFIED') AND old.type = 'DEBIT' AND old.account_id IS NOT NULL
        BEGIN
            UPDATE accounts SET balance = balance + old.amount WHERE id = old.account_id;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS revert_account_balance_on_delete_credit
        AFTER DELETE ON transactions
        WHEN old.status IN ('CONFIRMED', 'MODIFIED') AND old.type = 'CREDIT' AND old.account_id IS NOT NULL
        BEGIN
            UPDATE accounts SET balance = balance - old.amount WHERE id = old.account_id;
        END
        """.trimIndent()
    )

    for (stmt in statements) {
        executeQuietly(driver, stmt)
    }

    runMigrations(driver)
}

private fun runMigrations(driver: SqlDriver) {
    // Audit fix 1.6: these ALTERs used to run unconditionally and throw
    // "duplicate column name" on every launch for up-to-date DBs (error swallowed,
    // masking real migration failures). Each patch is now guarded by a schema probe
    // and runs at most once per column, with an explicit log line when applied.
    if (!columnExists(driver, "transactions", "is_merged")) {
        println("[DatabaseDriverFactory] patching schema: transactions.is_merged")
        executeDdl(driver, "ALTER TABLE transactions ADD COLUMN is_merged INTEGER NOT NULL DEFAULT 0;")
    }
    if (!columnExists(driver, "transactions", "merged_into_id")) {
        println("[DatabaseDriverFactory] patching schema: transactions.merged_into_id")
        executeDdl(driver, "ALTER TABLE transactions ADD COLUMN merged_into_id INTEGER;")
    }
    if (!columnExists(driver, "transactions", "payment_method")) {
        println("[DatabaseDriverFactory] patching schema: transactions.payment_method")
        executeDdl(driver, "ALTER TABLE transactions ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'UPI';")
    }
    if (!columnExists(driver, "transactions", "is_loan_given")) {
        println("[DatabaseDriverFactory] patching schema: transactions.is_loan_given")
        executeDdl(driver, "ALTER TABLE transactions ADD COLUMN is_loan_given INTEGER NOT NULL DEFAULT 0;")
    }
    executeDdl(driver, """
        CREATE TABLE IF NOT EXISTS merge_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            survivor_id INTEGER NOT NULL,
            merged_id INTEGER NOT NULL,
            merged_at INTEGER NOT NULL,
            FOREIGN KEY (survivor_id) REFERENCES transactions(id) ON DELETE CASCADE,
            FOREIGN KEY (merged_id) REFERENCES transactions(id) ON DELETE CASCADE
        );
    """.trimIndent())
    executeDdl(driver, "CREATE INDEX IF NOT EXISTS idx_transactions_is_merged ON transactions(is_merged);")
    executeDdl(driver, "CREATE INDEX IF NOT EXISTS idx_transactions_merged_into ON transactions(merged_into_id);")
    executeDdl(driver, "CREATE INDEX IF NOT EXISTS idx_merge_log_survivor ON merge_log(survivor_id);")
    executeDdl(driver, "CREATE INDEX IF NOT EXISTS idx_merge_log_merged ON merge_log(merged_id);")

    val needsBudgetMigration = try {
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info(budgets);",
            mapper = { cursor ->
                var hasCategory = false
                var hasLastAlertState = false
                while (cursor.next().value) {
                    val colName = cursor.getString(1)
                    if (colName == "category") hasCategory = true
                    if (colName == "last_alert_state") hasLastAlertState = true
                }
                QueryResult.Value(!hasCategory || !hasLastAlertState)
            },
            parameters = 0
        ).value
    } catch (_: Throwable) {
        false
    }

    if (needsBudgetMigration) {
        executeDdl(driver, "ALTER TABLE budgets ADD COLUMN category TEXT NOT NULL DEFAULT 'Global';")
        executeDdl(driver, "ALTER TABLE budgets ADD COLUMN last_alert_state TEXT;")
        executeDdl(driver, "ALTER TABLE budgets ADD COLUMN last_alert_at INTEGER;")
        executeDdl(driver, """
            CREATE TABLE IF NOT EXISTS budgets (
                id TEXT PRIMARY KEY NOT NULL,
                category TEXT NOT NULL,
                amount REAL NOT NULL,
                period TEXT NOT NULL,
                start_date INTEGER NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                last_alert_state TEXT,
                last_alert_at INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
        """.trimIndent())
        executeDdl(driver, "CREATE INDEX IF NOT EXISTS idx_budgets_category ON budgets(category);")
        executeDdl(driver, "CREATE INDEX IF NOT EXISTS idx_budgets_is_active ON budgets(is_active);")
    }

    executeDdl(driver, """
        CREATE TABLE IF NOT EXISTS learned_token (
            token TEXT PRIMARY KEY NOT NULL,
            transaction_count INTEGER NOT NULL DEFAULT 0,
            non_transaction_count INTEGER NOT NULL DEFAULT 0,
            first_seen INTEGER NOT NULL,
            last_updated INTEGER NOT NULL
        );
    """.trimIndent())
    executeDdl(driver, "CREATE INDEX IF NOT EXISTS idx_learned_token_counts ON learned_token(transaction_count, non_transaction_count);")

    executeDdl(driver, """
        CREATE TABLE IF NOT EXISTS recurring_pattern (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            merchant_normalized TEXT NOT NULL,
            amount_paise INTEGER NOT NULL,
            occurrence_count INTEGER NOT NULL,
            median_interval_hours REAL NOT NULL,
            cv REAL NOT NULL,
            period_hours INTEGER NOT NULL,
            autocorrelation_peak REAL NOT NULL,
            first_seen INTEGER NOT NULL,
            last_seen INTEGER NOT NULL,
            last_updated INTEGER NOT NULL,
            UNIQUE(merchant_normalized, amount_paise)
        );
    """.trimIndent())
    executeDdl(driver, "CREATE INDEX IF NOT EXISTS idx_pattern_merchant ON recurring_pattern(merchant_normalized);")
    executeDdl(driver, "CREATE INDEX IF NOT EXISTS idx_pattern_amount ON recurring_pattern(amount_paise);")

    executeDdl(driver, """
        CREATE TABLE IF NOT EXISTS ignored_packages (
            package_name TEXT PRIMARY KEY NOT NULL
        );
    """.trimIndent())
}

/**
 * Audit fix 1.6: schema probe used to guard idempotent column patches. Returns false on
 * probe failure so the (idempotent, IF-NOT-EXISTS-free) ALTER is still attempted — and
 * its failure, if any, is now logged loudly instead of swallowed.
 */
private fun columnExists(driver: SqlDriver, table: String, column: String): Boolean {
    return try {
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table);",
            mapper = { cursor ->
                var found = false
                while (cursor.next().value) {
                    if (cursor.getString(1) == column) found = true
                }
                QueryResult.Value(found)
            },
            parameters = 0
        ).value
    } catch (e: Throwable) {
        println("[DatabaseDriverFactory] schema probe failed for $table.$column: ${e.message}")
        false
    }
}

