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
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.presentation.CashBuddyApp
import com.cashbuddy.presentation.theme.CashBuddyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : FragmentActivity() {

    private val settingsRepository: SettingsRepository by inject()

    private enum class AuthState { LOADING, AUTHENTICATED, LOCKED }
    private var authState by mutableStateOf(AuthState.LOADING)
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

        // FLAG_SECURE temporarily disabled to allow device mirroring/scrcpy on laptop
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

        checkAndPromptNotificationPermissions()

        lifecycleScope.launch {
            val biometricEnabled = settingsRepository.getBiometricEnabled().firstOrNull() ?: false
            if (biometricEnabled) {
                isBiometricRequired = true
                authState = AuthState.LOCKED
                promptBiometricUnlock()
            } else {
                authState = AuthState.AUTHENTICATED
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

        // Layer 2: Handle incoming shared UPI payment screenshots
        val screenshotHandler = com.cashbuddy.screenshot.ScreenshotHandler(this)
        val cachedScreenshotFiles = mutableListOf<java.io.File>()

        if (intent?.action == android.content.Intent.ACTION_SEND && intent?.type?.startsWith("image/") == true) {
            val imageUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
            }
            if (imageUri != null) {
                screenshotHandler.copyUriToCache(imageUri)?.let { cachedScreenshotFiles.add(it) }
            }
        } else if (intent?.action == android.content.Intent.ACTION_SEND_MULTIPLE && intent?.type?.startsWith("image/") == true) {
            val uris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM)
            }
            uris?.forEach { uri ->
                screenshotHandler.copyUriToCache(uri)?.let { cachedScreenshotFiles.add(it) }
            }
        }

        // Process screenshots with user feedback
        var screenshotInitialRoute: String? = null
        if (cachedScreenshotFiles.isNotEmpty()) {
            screenshotInitialRoute = "processing_screenshot"
            lifecycleScope.launch(Dispatchers.IO) {
                val createdIds = screenshotHandler.processScreenshots(cachedScreenshotFiles)
                launch(Dispatchers.Main) {
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
            }
        }

        val initialRoute = screenshotInitialRoute
            ?: intent?.getStringExtra("EXTRA_NAVIGATE_TO")

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
                            initialRoute = initialRoute,
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
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // If no hardware/enrolled biometrics, allow access as fallback
                    if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                        errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                        errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE) {
                        authState = AuthState.AUTHENTICATED
                    }
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

