package com.cashbuddy

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    companion object {
        private const val TIMEOUT_LOCK_MS = 300_000L // 5 minutes
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Enforce FLAG_SECURE: Prevents screenshots and task manager leakage of sensitive finances
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

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

        // Load learned merchant rules into Rust CategoryEngine at app startup
        lifecycleScope.launch(Dispatchers.IO) {
            try {
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

                // Load learned trusted bank senders into Rust core
                val trustedSenderRepository: com.cashbuddy.domain.repository.TrustedSenderRepository by inject()
                val senders = trustedSenderRepository.getAllSenders().firstOrNull() ?: emptyList()
                for (s in senders) {
                    com.cashbuddy.core.learnTrustedSender(s.senderId)
                }
            } catch (e: Throwable) {
                android.util.Log.e("MainActivity", "Failed to load rules/trusted senders", e)
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
                        CashBuddyApp(initialRoute = initialRoute)
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
}
