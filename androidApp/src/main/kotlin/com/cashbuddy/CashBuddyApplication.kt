package com.cashbuddy

import android.app.Application
import com.cashbuddy.di.androidModule
import com.cashbuddy.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CashBuddyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Koin Dependency Injection
        startKoin {
            androidContext(this@CashBuddyApplication)
            modules(appModule, androidModule)
        }
    }
}
