package com.cashbuddy.di

import android.util.Log
import app.cash.sqldelight.db.SqlDriver
import com.cashbuddy.core.CryptoManager
import com.cashbuddy.core.NotificationParser
import com.cashbuddy.core.SecurityValidator
import com.cashbuddy.data.local.DatabaseDriverFactory
import com.cashbuddy.data.local.createDatabase
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.parser.KotlinNotificationParser
import org.koin.dsl.module

private const val TAG = "AndroidModule"

val androidModule = module {
    // Database Driver & Encrypted Database Instance
    single { com.cashbuddy.security.AndroidKeystoreManager(get()) }
    single<com.cashbuddy.platform.FileExporter> { com.cashbuddy.platform.AndroidFileExporter(get()) }
    single { DatabaseDriverFactory(get()) }
    single<SqlDriver> { get<DatabaseDriverFactory>().createDriver() }
    single<AppDatabase> { createDatabase(get<SqlDriver>()) }

    // Rust Core Native Singletons (lazy/fail-safe with logging)
    single { KotlinNotificationParser() }

    single {
        try {
            NotificationParser()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize Rust NotificationParser", e)
            null
        }
    }

    single {
        try {
            CryptoManager()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize Rust CryptoManager", e)
            null
        }
    }

    single {
        try {
            SecurityValidator()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize Rust SecurityValidator", e)
            null
        }
    }

    // Rust Priority Category Engine (Option C: Hybrid Rules + Tiny Personalization)
    single {
        try {
            com.cashbuddy.core.CategoryEngine()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize Rust CategoryEngine", e)
            null
        }
    }
}

