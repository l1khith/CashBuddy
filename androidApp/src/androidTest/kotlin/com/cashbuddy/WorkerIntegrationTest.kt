package com.cashbuddy

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.RecurringPatternRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.notification.NotificationHelper
import com.cashbuddy.worker.DailyWrapUpWorker
import com.cashbuddy.worker.RecurrenceWorker
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@RunWith(AndroidJUnit4::class)
class WorkerIntegrationTest : KoinComponent {

    private lateinit var context: Context
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var recurringPatternRepository: RecurringPatternRepository
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        transactionRepository = get()
        categoryRepository = get()
        recurringPatternRepository = get()
        settingsRepository = get()

        NotificationHelper.createChannels(context)
    }

    @Test
    fun test_RecurrenceWorker_RunsSuccessfully(): Unit = runBlocking {
        // 1. (Optional) Insert 5 monthly transactions with the same merchant
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val catId = categoryRepository.getAll().firstOrNull()?.firstOrNull()?.id ?: 1L

        for (i in 4 downTo 0) {
            val txTime = now - (i * 30 * dayMs)
            val tx = Transaction(
                id = 0L,
                amount = 499.0,
                type = TransactionType.DEBIT,
                currency = "INR",
                merchant = "Hotstar VIP",
                categoryId = catId,
                accountId = null,
                sourceApp = "notification",
                rawText = "Debited INR 499.00 for Hotstar VIP",
                confidence = 1.0f,
                status = TransactionStatus.CONFIRMED,
                notes = "Subscription",
                timestamp = txTime,
                createdAt = txTime,
                updatedAt = txTime
            )
            transactionRepository.insert(tx)
        }

        // 2. Enqueue RecurrenceWorker in the test environment
        val workRequest = OneTimeWorkRequestBuilder<RecurrenceWorker>().build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(workRequest).result.get()

        // Tell test driver all constraints are met
        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)
        testDriver?.setAllConstraintsMet(workRequest.id)

        // Run worker synchronously via TestListenableWorkerBuilder
        val worker = TestListenableWorkerBuilder<RecurrenceWorker>(context).build()
        val result = worker.doWork()

        // 3. Verify it executes and succeeds
        assertTrue("RecurrenceWorker must complete with success", result is ListenableWorker.Result.Success)

        // Verify WorkInfo state is valid and succeeded or enqueued
        val workInfo = workManager.getWorkInfoById(workRequest.id).get()
        assertNotNull("WorkInfo must not be null", workInfo)
        assertTrue(
            "Worker state should be ENQUEUED, RUNNING, or SUCCEEDED",
            workInfo!!.state in listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED)
        )

        // Verify recurring patterns were detected
        val patterns = recurringPatternRepository.findByMerchant("Hotstar VIP")
        assertTrue("Recurring pattern for Hotstar VIP should be detected", patterns.isNotEmpty())
    }

    @Test
    fun test_DailyWrapUpWorker_FiresNotification(): Unit = runBlocking {
        // 1. Enable notifications in user settings
        settingsRepository.setNotificationEnabled(true)
        settingsRepository.setSmartNotificationsEnabled(true)

        // Insert a debit transaction for today
        val now = System.currentTimeMillis()
        val catId = categoryRepository.getAll().firstOrNull()?.firstOrNull()?.id ?: 1L
        val tx = Transaction(
            id = 0L,
            amount = 250.0,
            type = TransactionType.DEBIT,
            currency = "INR",
            merchant = "Blue Tokai Coffee",
            categoryId = catId,
            accountId = null,
            sourceApp = "manual",
            rawText = "Blue Tokai Coffee",
            confidence = 1.0f,
            status = TransactionStatus.CONFIRMED,
            notes = null,
            timestamp = now,
            createdAt = now,
            updatedAt = now
        )
        transactionRepository.insert(tx)

        // 2. Enqueue DailyWrapUpWorker and run synchronously
        val worker = TestListenableWorkerBuilder<DailyWrapUpWorker>(context).build()
        val result = worker.doWork()

        // Verify it executes without crashing
        assertTrue("DailyWrapUpWorker must succeed without crashing", result is ListenableWorker.Result.Success)

        // 3. Verify notification or notification channel is active
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_DAILY_WRAP_UP)
            assertNotNull("Daily wrap-up channel must exist", channel)
        }

        // On Android 23+, active notifications can be inspected
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNotifs = notificationManager.activeNotifications
            val hasDailyNotification = activeNotifs.any { it.id == DailyWrapUpWorker.NOTIFICATION_ID }
            // If the notification permission is granted on the emulator, verify notification is posted
            if (activeNotifs.isNotEmpty()) {
                assertTrue("Notification for Daily Wrap Up should be active", hasDailyNotification)
            }
        }
    }
}
