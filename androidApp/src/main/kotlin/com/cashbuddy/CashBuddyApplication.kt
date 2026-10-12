package com.cashbuddy

import android.app.Application
import android.util.Log
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

        // Initialize Koin Dependency Injection
        startKoin {
            androidContext(this@CashBuddyApplication)
            modules(appModule, androidModule, platformModule)
        }

        // Configure DebugConfig with BuildConfig.DEBUG and dynamic app version info
        get<DebugConfig>().isDebugBuild = BuildConfig.DEBUG
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
