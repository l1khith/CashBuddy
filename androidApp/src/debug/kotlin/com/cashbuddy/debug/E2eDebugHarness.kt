// NO-NETWORK
package com.cashbuddy.debug

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToLong

/**
 * Debug-only E2E harness companion for docs/E2E_NOTIFICATION_TEST_PLAN.md.
 *
 * Renders a small "E2E" pill (visible ONLY on debuggable builds) that opens a
 * bottom sheet. Default mode is DRY-RUN: typed/fixed payloads are evaluated
 * through a READ-ONLY mirror of MessagePipeline.ingest stages 2–6 and the
 * report shows exactly what the classifier/policy/dedup would decide — with
 * nothing stored. Toggling the switch off restores live "Post" behavior, where
 * the synthetic notification goes through the real listener service.
 * Feature is a no-op on release builds.
 */
private const val E2E_CHANNEL_ID = "e2e_debug_harness"

/** Mirrors plan §3 rows; (planId, title, body). Same body reused across posts so T8 (UTR dedup merge) and T9 (soft dedup) work as written. */
private val E2E_TESTS: List<Triple<String, String, String>> = listOf(
    Triple("T1", "E2ETEST Pay: Rs.540.00 debited", "INR 540.00 debited from A/c XX4321 on 29-10-26. UPI Ref 123456789012"),
    Triple("T2", "E2ETEST Bank: Rs.1,200.00 credited", "INR 1200 credited to A/c XX9876"),
    Triple("T3", "E2ETEST Pay: Rs.15,000.00 debited", "INR 15000 debited from A/c XX4321"), // >= ₹10k → PENDING (human-in-the-loop rule)
    Triple("T5", "E2ETEST Bank: OTP", "OTP is 4812 for your transaction. Do not share."), // must be IGNORED by classifier
    Triple("T6", "E2ETEST Deals: Festive Offer!", "Get 10% cashback this festive season, limited offer"), // must be IGNORED
    Triple("T7", "E2ETEST Pay: Rs.0.00 debited", "INR 0.00 debited from A/c XX4321") // must be dropped pre-insert
)

/** @return true if the installed APK is debuggable (debug variants + `-g` installs). */
fun isDebuggableBuild(context: Context): Boolean =
    (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun E2eDebugHarness() {
    val context = LocalContext.current
    if (!isDebuggableBuild(context)) return // release builds: render nothing, zero overhead branch

    var expanded by remember { mutableStateOf(false) }
    var sheetReport by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Dry-run: analyze the parse WITHOUT storing anything. Default ON — nothing is
    // persisted (no raw_message, no tx, no budget/pattern/read-state writes).
    var dryRun by remember { mutableStateOf(true) }
    // Freeform custom post (send ANY notification content, not just the fixed T-rows)
    var customTitle by remember { mutableStateOf("") }
    var customBody by remember { mutableStateOf("") }

    // Single action entry: DRY-RUN evaluates read-only; live mode posts through the listener.
    val runAction: (String, String, String) -> Unit = @androidx.annotation.RequiresPermission(
        android.Manifest.permission.POST_NOTIFICATIONS
    ) { planId, title, body ->
        if (dryRun) {
            scope.launch {
                sheetReport = try {
                    runDryRunAnalysis(title, body)
                } catch (t: Throwable) {
                    "DRY-RUN FAILED: ${t.message ?: t::class.simpleName}"
                }
            }
        } else {
            sheetReport = postE2eNotification(context, planId, title, body)
        }
    }

    // Discreet floating pill, mid-left edge (avoids status bar and app bottom nav).
    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(6.dp)
                .clickable { expanded = true }
        ) {
            Text(
                text = "E2E",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }

    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text("E2E Notification Test Harness", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Companion to docs/E2E_NOTIFICATION_TEST_PLAN.md — analyzes the REAL pipeline either without storage (dry-run) or by posting through the listener.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Dry-run switch: default ON so examination NEVER writes to the live DB
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "DRY-RUN: analyze only — NOTHING stored",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Switch(checked = dryRun, onCheckedChange = { dryRun = it })
                }
                ListenerStatusRow()
                Spacer(modifier = Modifier.height(12.dp))

                E2E_TESTS.forEach { (planId, title, body) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("$planId · $title", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = when (planId) {
                                    "T1" -> "expect: confirmed tx ₹540 DEBIT + UTR (repeat → T8 merge)"
                                    "T2" -> "expect: CREDIT ₹1,200"
                                    "T3" -> "expect: PENDING in Review (≥ ₹10k)"
                                    "T5" -> "expect: ignored (OTP LR 0.02)"
                                    "T6" -> "expect: ignored (offer LR 0.10)"
                                    "T7" -> "expect: dropped pre-insert (amount 0)"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Button(onClick = { runAction(planId, title, body) }) {
                            Text(if (dryRun) "Analyze" else "Post")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "DRY-RUN: pressing a button shows what the pipeline WOULD decide — nothing touches the DB. Toggle the switch off for live Post mode (writes real rows). T8 dedup in LIVE mode: press T1 twice — same UTR expects tier-1 merge. T9 soft-dedup and scripted T10+ remain adb-only (plan §3).",
                    style = MaterialTheme.typography.bodySmall
                )
                sheetReport?.let { report ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                            .padding(10.dp)
                    ) {
                        Text(text = report, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // ── Freeform: post ANY notification content ──
                Spacer(modifier = Modifier.height(16.dp))
                Text("Custom (any content, any payload)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("Notification title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customBody,
                    onValueChange = { customBody = it },
                    label = { Text("Notification body (raw SMS text)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "DRY-RUN: analyze any text freely (bank msg, any app notification, weird formats…). Live mode posts it through the listener instead (T4 low-confidence etc.).",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        enabled = customTitle.isNotBlank() && customBody.isNotBlank(),
                        onClick = { runAction("T-CUSTOM", customTitle, customBody) }
                    ) { Text(if (dryRun) "Analyze" else "Post") }
                }
            }
        }
    }
}

@Composable
private fun ListenerStatusRow() {
    val context = LocalContext.current
    val listenerBound = remember {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    Text(
        text = if (listenerBound) {
            "NotificationListenerService: BOUND ✓"
        } else {
            "NotificationListenerService: NOT ENABLED — plan §1.1 grant first (/ adb shell cmd notification allow_listener com.cashbuddy/com.cashbuddy.service.TransactionNotificationListener)"
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
private fun postE2eNotification(context: Context, planId: String, title: String, body: String): String {
    return try {
        ensureE2eChannel(context)
        val notification = NotificationCompat.Builder(context, E2E_CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        // Same notification id per planId so re-posts REPLACE the rail entry (stable, no PK-style collisions vs. our own review banners)
        NotificationManagerCompat.from(context).notify(title, planId.hashCode(), notification)
        "$planId posted → listener should ingest: check Review / Home / DebugLog screens to verify expected row above."
    } catch (t: Throwable) {
        "$planId FAILED: ${t.message ?: t::class.simpleName} (POST_NOTIFICATIONS revoked? listener not bound?)"
    }
}

private fun ensureE2eChannel(context: Context) {    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                E2E_CHANNEL_ID,
                "E2E Debug Harness",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }
}

/**
 * DRY-RUN analysis: mirrors [com.cashbuddy.core.prob.MessagePipeline.ingest]
 * stages 2–6 (`MessagePipeline.kt:83–171`) as a strictly READ-ONLY evaluation —
 * no raw_message insert, no transaction insert, and no write of any kind.
 * The recurring-pattern, settings and dedup-window queries run as READS only,
 * exactly as production would evaluate them; the report describes what the
 * live path WOULD have stored/ignored/merged.
 */
private suspend fun runDryRunAnalysis(title: String, body: String): String {
    val koin = org.koin.core.context.GlobalContext.get()
    val sourceDetector = koin.get<com.cashbuddy.core.prob.SourceDetector>()
    val evidenceExtractor = koin.get<com.cashbuddy.core.prob.EvidenceExtractor>()
    val classifier = koin.get<com.cashbuddy.core.prob.ProbabilisticClassifier>()
    val recurringRepo = koin.getOrNull<com.cashbuddy.domain.repository.RecurringPatternRepository>()
    val settingsRepo = koin.getOrNull<com.cashbuddy.domain.repository.SettingsRepository>()
    val transactionRepo = koin.getOrNull<com.cashbuddy.domain.repository.TransactionRepository>()

    val now = com.cashbuddy.platform.currentTimeMillis()
    val raw = com.cashbuddy.core.prob.RawMessage(
        id = "dryrun",
        sourceType = com.cashbuddy.core.prob.SourceType.NOTIFICATION,
        packageName = null,
        senderId = null,
        title = title,
        text = body,
        timestamp = now
    )
    val sb = StringBuilder()

    // Stage 2: source detection (pipeline passes raw.packageName ?: raw.senderId ?: "")
    val source = sourceDetector.detect("", raw.text)

    // Stage 3: evidence extraction (only READS from recent-state)
    val evidence = evidenceExtractor.extract(raw, source)

    // Stage 4: classification
    val fullText = if (raw.title.isNotBlank()) "${raw.title} ${raw.text}" else raw.text
    var classification = classifier.classifySuspending(evidence, fullText, source, raw.packageName)

    // Stage 4.1: recurring-pattern check (T6 / AC-2) — read-only
    var isRecurringMatch = false
    val dryMerchant = classification.merchant
    val dryAmount = classification.amount
    if (dryMerchant != null && dryAmount != null && dryAmount > 0.0) {
        val amountPaise = (dryAmount * 100.0).roundToLong()
        if (recurringRepo?.findByMerchantAndAmount(dryMerchant, amountPaise) != null) {
            isRecurringMatch = true
            classification = classification.copy(pTransaction = maxOf(classification.pTransaction, 0.95))
        }
    }

    // Stage 5: policy decision (reads the persisted auto-confirm threshold; falls back to 10000)
    val maxAutoConfirm = settingsRepo?.getAutoConfirmThreshold()?.firstOrNull() ?: 10000.0
    val action = if (isRecurringMatch && (classification.amount ?: 0.0) < maxAutoConfirm) {
        com.cashbuddy.core.prob.PolicyEngine.Action.AUTO_LOG
    } else {
        com.cashbuddy.core.prob.PolicyEngine().action(
            p = classification.pTransaction,
            hasAccount = classification.accountLast4 != null,
            amount = classification.amount ?: 0.0,
            maxAutoConfirmAmount = maxAutoConfirm
        )
    }

    sb.appendLine("🛡 DRY-RUN — NOTHING STORED (read-only pipeline mirror)")
    sb.appendLine("Action: ${action.name} · Source: ${source.name} · P(tx): %.4f".format(classification.pTransaction))
    classification.amount?.let { sb.appendLine("Amount: ₹%.2f".format(it)) }
    classification.type?.let { sb.appendLine("Type: $it") }
    classification.accountLast4?.let { sb.appendLine("Account: XX$it") }
    sb.appendLine("UTR: ${classification.utr ?: "—"} · Merchant: ${classification.merchant ?: "—"}")
    classification.fieldConfidences.let { fc ->
        sb.appendLine("Field conf: amt %.2f · type %.2f · acc %.2f · merch %.2f".format(fc.amount, fc.type, fc.accountLast4, fc.merchant))
    }
    if (isRecurringMatch) sb.appendLine("Recurring match: YES (p raised to ≥0.95)")

    when (action) {
        com.cashbuddy.core.prob.PolicyEngine.Action.IGNORE ->
            sb.appendLine("→ Would be IGNORED: P below the 0.30 gate — nothing stored even in live mode.")
        else -> {
            val amount = classification.amount ?: 0.0
            if (amount <= 0.0) {
                sb.appendLine("→ Would be dropped pre-insert: zero/unparsed amount (live mode stores nothing).")
            } else {
                // Stage 6: dedup over the SAME window the pipeline uses — read-only candidates
                val utr = classification.utr
                val windowMs = if (utr != null) 24L * 60 * 60 * 1000 else com.cashbuddy.core.prob.DedupEngine.WINDOW_MS
                val recentTxs = transactionRepo
                    ?.getByDateRange(now - windowMs, now + windowMs)
                    ?.firstOrNull() ?: emptyList()
                val candidates = recentTxs.map {
                    val candidateUtr = it.utr
                        ?: evidenceExtractor.extractUtr(it.rawText)
                        ?: (it.notes?.let { n -> evidenceExtractor.extractUtr(n) })
                    com.cashbuddy.core.prob.DedupEngine.ExistingCandidate(
                        id = it.id,
                        amount = it.amount,
                        merchant = it.merchant,
                        accountLast4 = it.sourceApp,
                        pTransaction = it.confidence.toDouble(),
                        timestamp = it.timestamp,
                        utr = candidateUtr
                    )
                }
                val decision = com.cashbuddy.core.prob.DedupEngine.resolve(
                    newTx = com.cashbuddy.core.prob.DedupEngine.DedupInput(
                        amount = amount,
                        merchant = classification.merchant,
                        accountLast4 = classification.accountLast4,
                        pTransaction = classification.pTransaction,
                        timestamp = raw.timestamp,
                        utr = utr
                    ),
                    existing = candidates
                )
                sb.appendLine(
                    when (decision) {
                        is com.cashbuddy.core.prob.DedupEngine.Decision.Insert ->
                            "→ Dedup: INSERT (would create a new row; no near-duplicate in ±${windowMs / 60000}min window)"
                        is com.cashbuddy.core.prob.DedupEngine.Decision.Skip ->
                            "→ Dedup: SKIP"
                        is com.cashbuddy.core.prob.DedupEngine.Decision.Merge ->
                            "→ Dedup: MERGE into tx #${decision.existingId} (UTR/soft match — would upgrade fields, not add a row)"
                    }
                )
            }
        }
    }

    // Evidence trace: top-3 signal contributions + learned-token evidence
    sb.appendLine()
    sb.appendLine("Top contributors:")
    classification.contributions
        .sortedByDescending { abs(it.delta) }
        .take(3)
        .forEach { sb.appendLine("· ${it.signal}: LR %.2f (Δ %+.2f)".format(it.lr, it.delta)) }
    if (evidence.tokenLogOdds != 0.0) {
        sb.appendLine("· learned_tokens: LR %.2f (Δ %+.2f)".format(exp(evidence.tokenLogOdds), evidence.tokenLogOdds))
    }
    return sb.toString()
}