package com.cashbuddy

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.core.prob.MessagePipeline
import com.cashbuddy.core.prob.PipelineOutcome
import com.cashbuddy.core.prob.RawMessage
import com.cashbuddy.core.prob.SourceType
import com.cashbuddy.ocr.OcrEngine
import com.cashbuddy.presentation.CashBuddyApp
import com.cashbuddy.presentation.theme.CashBuddyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.util.UUID

class MainActivity : FragmentActivity() {

    private val settingsRepository: SettingsRepository by inject()
    private val ocrEngine: OcrEngine by inject()
    private val pipeline: MessagePipeline by inject()

    private enum class AuthState { LOADING, AUTHENTICATED, LOCKED }
    private var authState by mutableStateOf(AuthState.LOADING)
    private var currentNavigationRoute by mutableStateOf<String?>(null)
    private var pendingClipboardUtr by mutableStateOf<String?>(null)
    private var isBiometricRequired = false
    private var lastBackgroundTimestamp: Long = 0L

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // POST_NOTIFICATIONS result handled
        }

    companion object {
        private const val TIMEOUT_LOCK_MS = 300_000L // 5 minutes
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            // Disabled in debug builds to allow device mirroring/scrcpy on laptop
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        checkAndPromptNotificationPermissions()

        if (savedInstanceState != null) {
            lastBackgroundTimestamp = savedInstanceState.getLong("KEY_LAST_BACKGROUND_TIMESTAMP", 0L)
            isBiometricRequired = savedInstanceState.getBoolean("KEY_BIOMETRIC_REQUIRED", false)
            currentNavigationRoute = savedInstanceState.getString("KEY_CURRENT_NAV_ROUTE")
            val savedAuth = savedInstanceState.getString("KEY_AUTH_STATE")
            val restoredState = savedAuth?.let { runCatching { AuthState.valueOf(it) }.getOrNull() }
            if (restoredState == AuthState.AUTHENTICATED) {
                authState = AuthState.AUTHENTICATED
            }
        }

        if (savedInstanceState == null || authState == AuthState.LOADING) {
            lifecycleScope.launch {
                val biometricEnabled = settingsRepository.getBiometricEnabled().firstOrNull() ?: false
                if (biometricEnabled) {
                    isBiometricRequired = true
                    authState = AuthState.LOCKED
                    promptBiometricUnlock()
                } else {
                    authState = AuthState.AUTHENTICATED
                    checkClipboardForUtr()
                }
            }
        }

        // Seed defaults and load learned merchant rules and trusted senders into pure Kotlin core
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val categoryRepo: com.cashbuddy.domain.repository.CategoryRepository by inject()
                val accountRepo: com.cashbuddy.domain.repository.AccountRepository by inject()
                categoryRepo.seedDefaults(now)
                accountRepo.seedDefaults(now)

                val merchantRuleRepository: com.cashbuddy.domain.repository.MerchantRuleRepository by inject()
                val categoryEngine: com.cashbuddy.core.CategoryEngine by inject()
                val rules = merchantRuleRepository.getAll().firstOrNull() ?: emptyList()
                val entries = rules.map {
                    com.cashbuddy.core.MerchantRuleEntry(
                        merchant = it.pattern,
                        category = it.categoryName ?: "Unknown"
                    )
                }
                categoryEngine.loadUserRules(entries)
            } catch (e: Throwable) {
                android.util.Log.e("MainActivity", "Failed to seed defaults or load rules", e)
            }
        }

        // Layer 2: Handle incoming share intents (text/plain from GPay or image/* screenshots)
        if (savedInstanceState == null) {
            val initialRoute = intent?.getStringExtra("EXTRA_NAVIGATE_TO")
            if (!initialRoute.isNullOrBlank()) {
                currentNavigationRoute = initialRoute
            }
            handleIncomingShareIntent(intent)
        }

        setContent {
            CashBuddyTheme {
                when (authState) {
                    AuthState.LOADING -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        )
                    }
                    AuthState.AUTHENTICATED -> {
                        CashBuddyApp(
                            initialRoute = currentNavigationRoute,
                            onRouteConsumed = { currentNavigationRoute = null },
                            clipboardUtr = pendingClipboardUtr,
                            onConsumeClipboardUtr = {
                                pendingClipboardUtr = null
                                com.cashbuddy.clipboard.ClipboardMonitor.setLastProcessedUtr(null)
                            },
                            onOpenNotificationSettings = {
                                try {
                                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                } catch (e: Throwable) {
                                    android.util.Log.e("MainActivity", "Failed to open notification settings", e)
                                }
                            }
                        )
                    }
                    AuthState.LOCKED -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "CashBuddy is Locked",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Biometric authentication is required to access your financial records.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { promptBiometricUnlock() }) {
                                    Text("Unlock with Biometrics")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val route = intent.getStringExtra("EXTRA_NAVIGATE_TO")
        if (!route.isNullOrBlank()) {
            currentNavigationRoute = route
        }
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        val intentType = intent.type
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()

        when {
            intentType?.startsWith("text/") == true || (!sharedText.isNullOrBlank() && intentType == null) -> {
                // GPay / PhonePe / Paytm / Bank Share Flow — NO OCR
                if (!sharedText.isNullOrBlank()) {
                    handleSharedText(sharedText)
                }
            }
            intentType?.startsWith("image/") == true -> {
                // Screenshot Flow — RUN OCR
                handleSharedImages(intent)
            }
        }
    }

    private fun handleSharedText(text: String) {
        val raw = RawMessage(
            id = UUID.randomUUID().toString(),
            sourceType = SourceType.UPI_RECEIPT,
            packageName = "share_sheet",
            senderId = null,
            title = "UPI Receipt",
            text = text,
            timestamp = System.currentTimeMillis()
        )
        currentNavigationRoute = "review_inbox"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outcome = pipeline.ingest(raw)
                withContext(Dispatchers.Main) {
                    when (outcome) {
                        is PipelineOutcome.Logged -> {
                            val formattedAmt = if (outcome.amount == outcome.amount.toLong().toDouble()) {
                                outcome.amount.toLong().toString()
                            } else {
                                outcome.amount.toString()
                            }
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "Payment of ₹$formattedAmt at ${outcome.merchant} auto-logged!",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        is PipelineOutcome.PendingReview -> {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "Receipt received! Added to Review Inbox.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        is PipelineOutcome.Merged -> {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "Receipt matched with transaction and merged.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        is PipelineOutcome.DuplicateSkipped -> {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "Transaction already recorded (duplicate skipped).",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        is PipelineOutcome.Ignored -> {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "Could not recognize transaction in shared text.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("MainActivity", "Failed to ingest shared text receipt", e)
            }
        }
    }

    private fun handleSharedImages(intent: Intent) {
        if (!ocrEngine.isAvailable) {
            android.widget.Toast.makeText(
                this,
                "Screenshot OCR is not available on this device.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }

        val screenshotHandler = com.cashbuddy.screenshot.ScreenshotHandler(this)
        val cachedScreenshotFiles = mutableListOf<java.io.File>()

        if (intent.action == Intent.ACTION_SEND) {
            val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (imageUri != null) {
                screenshotHandler.copyUriToCache(imageUri)?.let { cachedScreenshotFiles.add(it) }
            }
        } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            uris?.forEach { uri ->
                screenshotHandler.copyUriToCache(uri)?.let { cachedScreenshotFiles.add(it) }
            }
        }

        if (cachedScreenshotFiles.isNotEmpty()) {
            currentNavigationRoute = "processing_screenshot"
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val createdIds = screenshotHandler.processScreenshots(cachedScreenshotFiles)
                    withContext(Dispatchers.Main) {
                        if (createdIds.isNotEmpty()) {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "${createdIds.size} transaction(s) parsed from screenshot",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } else {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "Could not recognize transaction in screenshot",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } finally {
                    cachedScreenshotFiles.forEach { file ->
                        runCatching { file.delete() }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("KEY_AUTH_STATE", authState.name)
        outState.putLong("KEY_LAST_BACKGROUND_TIMESTAMP", lastBackgroundTimestamp)
        outState.putBoolean("KEY_BIOMETRIC_REQUIRED", isBiometricRequired)
        outState.putString("KEY_CURRENT_NAV_ROUTE", currentNavigationRoute)
    }

    override fun onResume() {
        super.onResume()
        if (authState == AuthState.AUTHENTICATED) {
            checkClipboardForUtr()
        }
    }

    private fun checkClipboardForUtr() {
        com.cashbuddy.clipboard.ClipboardMonitor.checkClipboard(this)?.let { utr ->
            pendingClipboardUtr = utr
        }
    }

    override fun onStart() {
        super.onStart()
        if (isBiometricRequired && lastBackgroundTimestamp > 0) {
            val elapsed = System.currentTimeMillis() - lastBackgroundTimestamp
            if (elapsed >= TIMEOUT_LOCK_MS) {
                authState = AuthState.LOCKED
                promptBiometricUnlock()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lastBackgroundTimestamp = System.currentTimeMillis()
    }

    private fun promptBiometricUnlock() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    authState = AuthState.AUTHENTICATED
                    checkClipboardForUtr()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Security non-negotiable: Never auto-unlock on error.
                    // Keep authState as LOCKED. User must tap "Unlock" or retry with PIN/biometrics.
                    authState = AuthState.LOCKED
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("CashBuddy Authentication")
            .setSubtitle("Authenticate to access your offline ledger")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun checkAndPromptNotificationPermissions() {
        // Suppress during automated benchmark runs
        if (intent.getBooleanExtra("skip_permission_prompts", false) || BuildConfig.BUILD_TYPE == "benchmark") {
            return
        }

        // 1. Android 13+ POST_NOTIFICATIONS runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. NotificationListenerService Access check
        val isListenerGranted = NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
        if (!isListenerGranted) {
            showNotificationListenerDialog()
        }
    }

    private fun showNotificationListenerDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Enable Automatic Expense Tracking")
            .setMessage("CashBuddy operates 100% offline and captures transactions passively from bank and UPI notifications. Please enable Notification Access for CashBuddy in Android settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (e: Throwable) {
                    android.util.Log.e("MainActivity", "Could not open notification settings", e)
                }
            }
            .setNegativeButton("Later", null)
            .show()
    }
}

