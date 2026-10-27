package com.cashbuddy

import android.app.Application
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.cashbuddy.debug.DebugConfig
import com.cashbuddy.di.androidModule
import com.cashbuddy.di.appModule
import com.cashbuddy.di.platformModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.io.File
import java.io.FileOutputStream

class CashBuddyApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val assistantModule = org.koin.dsl.module {
            single<com.cashbuddy.core.anomaly.AnomalyNotifier> {
                com.cashbuddy.notification.AndroidAnomalyNotifier(get(), get())
            }
        }

        // Initialize Koin Dependency Injection
        startKoin {
            androidContext(this@CashBuddyApplication)
            modules(appModule, androidModule, platformModule, assistantModule)
        }

        // Setup notification channels for Assistant features
        com.cashbuddy.notification.NotificationHelper.createChannels(this)

        // Observe smart notifications setting to schedule or cancel workers & persistent notification
        applicationScope.launch {
            try {
                val settingsRepository: com.cashbuddy.domain.repository.SettingsRepository = get()
                settingsRepository.getSmartNotificationsEnabled().collect { enabled ->
                    if (enabled) {
                        com.cashbuddy.worker.AssistantWorkManager.scheduleAll(this@CashBuddyApplication)
                        com.cashbuddy.notification.QuickGlanceNotification.update(this@CashBuddyApplication)
                    } else {
                        com.cashbuddy.worker.AssistantWorkManager.cancelAll(this@CashBuddyApplication)
                        com.cashbuddy.notification.QuickGlanceNotification.dismiss(this@CashBuddyApplication)
                    }
                }
            } catch (e: Throwable) {
                Log.e("CashBuddyApplication", "Failed to initialize Assistant workers", e)
            }
        }

        // Listen for transaction updates to refresh persistent notification & Glance widget
        val filter = android.content.IntentFilter("com.cashbuddy.ACTION_TRANSACTIONS_UPDATED")
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                    applicationScope.launch {
                        com.cashbuddy.notification.QuickGlanceNotification.update(this@CashBuddyApplication)
                        try {
                            com.cashbuddy.widget.CashBuddyWidget().updateAll(this@CashBuddyApplication)
                        } catch (e: Throwable) {
                            Log.w("CashBuddyApplication", "Widget update failed", e)
                        }
                    }
                }
            },
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Configure DebugConfig with BuildConfig.DEBUG and dynamic app version info
        get<DebugConfig>().isDebugBuild = BuildConfig.DEBUG
        com.cashbuddy.platform.setAppContext(this)
        com.cashbuddy.platform.setAppVersionInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE
        )

        // First-run warmup for F-Droid flavor: pre-copy bundled tessdata from assets
        if (BuildConfig.FLAVOR == "fdroid") {
            applicationScope.launch {
                try {
                    val tessDataDir = File(filesDir, "tessdata")
                    if (!tessDataDir.exists()) {
                        tessDataDir.mkdirs()
                    }
                    val assetFiles = assets.list("tessdata") ?: emptyArray()
                    for (fileName in assetFiles) {
                        if (!fileName.endsWith(".traineddata")) continue
                        val destFile = File(tessDataDir, fileName)
                        val assetBytes = assets.open("tessdata/$fileName").use { it.readBytes() }
                        if (!destFile.exists() || destFile.length() != assetBytes.size.toLong()) {
                            FileOutputStream(destFile).use { output ->
                                output.write(assetBytes)
                                output.flush()
                            }
                            Log.d("CashBuddyApplication", "Warmup copied tessdata/$fileName (${assetBytes.size} bytes)")
                        }
                    }
                } catch (e: Throwable) {
                    Log.w("CashBuddyApplication", "Tessdata warmup failed", e)
                }
            }
        }
    }
}
