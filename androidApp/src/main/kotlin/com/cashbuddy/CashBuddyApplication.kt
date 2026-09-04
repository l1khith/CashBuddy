package com.cashbuddy

import android.app.Application
import com.cashbuddy.di.androidModule
import com.cashbuddy.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CashBuddyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Attempt loading native Rust core library
        try {
            System.loadLibrary("cashbuddy_core")
        } catch (_: Throwable) {
            // Failsafe: When native library is absent or running without NDK binaries,
            // the system continues seamlessly with the pure Kotlin parser & security engine.
        }

        // 2. Initialize Koin Dependency Injection
        startKoin {
            androidContext(this@CashBuddyApplication)
            modules(appModule, androidModule)
        }
    }
}
