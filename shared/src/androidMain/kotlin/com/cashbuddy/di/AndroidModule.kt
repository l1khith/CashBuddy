package com.cashbuddy.di

import app.cash.sqldelight.db.SqlDriver
import com.cashbuddy.core.CryptoManager
import com.cashbuddy.core.NotificationParser
import com.cashbuddy.core.SecurityValidator
import com.cashbuddy.data.local.DatabaseDriverFactory
import com.cashbuddy.data.local.createDatabase
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.parser.KotlinNotificationParser
import org.koin.dsl.module

val androidModule = module {
    // Database Driver & Encrypted Database Instance
    single { com.cashbuddy.security.AndroidKeystoreManager(get()) }
    single<com.cashbuddy.platform.FileExporter> { com.cashbuddy.platform.AndroidFileExporter(get()) }
    single { DatabaseDriverFactory(get()) }
    single<SqlDriver> { get<DatabaseDriverFactory>().createDriver() }
    single<AppDatabase> { createDatabase(get<SqlDriver>()) }

    // Rust Core Native Singletons (lazy/fail-safe)
    single { KotlinNotificationParser() }

    single {
        try {
            NotificationParser()
        } catch (_: Throwable) {
            null
        }
    }

    single {
        try {
            CryptoManager()
        } catch (_: Throwable) {
            null
        }
    }

    single {
        try {
            SecurityValidator()
        } catch (_: Throwable) {
            null
        }
    }
}
