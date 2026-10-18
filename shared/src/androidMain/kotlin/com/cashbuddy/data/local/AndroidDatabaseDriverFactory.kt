package com.cashbuddy.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.security.AndroidKeystoreManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        val keystoreManager = AndroidKeystoreManager(context)
        val passphrase = keystoreManager.getOrCreateDatabasePassphrase()

        try {
            System.loadLibrary("sqlcipher")
        } catch (_: Throwable) {
            // Failsafe for test environments where sqlcipher native libs may not be bundled
        }

        val driver = AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = context,
            name = "cashbuddy.db",
            factory = SupportOpenHelperFactory(passphrase.copyOf()),
            callback = object : AndroidSqliteDriver.Callback(AppDatabase.Schema) {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("CREATE TABLE IF NOT EXISTS ignored_packages (package_name TEXT PRIMARY KEY NOT NULL);")
                }
            }
        )

        // Zeroize original passphrase from heap — factory holds its own copy for lazy DB open
        java.util.Arrays.fill(passphrase, 0.toByte())

        return driver
    }
}
