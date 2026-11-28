package com.cashbuddy

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.RawMessageRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.service.TransactionNotificationListener
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@RunWith(AndroidJUnit4::class)
class NotificationServiceTest : KoinComponent {

    private lateinit var context: Context
    private lateinit var listener: TestableNotificationListener
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var rawMessageRepository: RawMessageRepository

    class TestableNotificationListener(context: Context) : TransactionNotificationListener() {
        init {
            attachBaseContext(context)
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        transactionRepository = get()
        rawMessageRepository = get()
        listener = TestableNotificationListener(context)
        listener.onCreate()
    }

    @After
    fun tearDown() {
        listener.onDestroy()
    }

    private fun createMockSbn(
        packageName: String,
        title: String,
        text: String
    ): StatusBarNotification {
        val sbn = mockk<StatusBarNotification>(relaxed = true)
        every { sbn.packageName } returns packageName
        every { sbn.isOngoing } returns false
        val now = System.currentTimeMillis()
        every { sbn.postTime } returns now

        val notification = Notification()
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, title)
            putCharSequence(Notification.EXTRA_TEXT, text)
        }
        notification.extras = extras
        every { sbn.notification } returns notification
        return sbn
    }

    @Test
    fun test_Notification_ParsesBankSMS(): Unit = runBlocking {
        val text = "Rs.500 debited from A/c XX1234"
        val sbn = createMockSbn(
            packageName = "com.google.android.apps.messaging",
            title = "VK-HDFCBK",
            text = text
        )

        listener.onNotificationPosted(sbn)

        // Polling loop to wait for async ingest in serviceScope
        var detectedTx: Transaction? = null
        val deadline = System.currentTimeMillis() + 6000L
        while (System.currentTimeMillis() < deadline) {
            val all = transactionRepository.getAll().firstOrNull().orEmpty()
            detectedTx = all.find { it.amount == 500.0 && it.rawText.contains("XX1234") }
            if (detectedTx != null) break
            kotlinx.coroutines.delay(100)
        }

        assertNotNull("Expected transaction was not saved to database", detectedTx)
        assertEquals(500.0, detectedTx!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, detectedTx.type)

        // Verify RawMessage is ingested
        val rawMessages = rawMessageRepository.getRecent(50)
        val matchingRaw = rawMessages.find { it.text.contains("Rs.500 debited") }
        assertNotNull("RawMessage was not found in rawMessageRepository", matchingRaw)
    }

    @Test
    fun test_Notification_IgnoresPromos(): Unit = runBlocking {
        val countBefore = transactionRepository.getAll().firstOrNull()?.size ?: 0
        val sbn = createMockSbn(
            packageName = "com.zomato",
            title = "Zomato Deals",
            text = "Get ₹100 off your next order!"
        )

        listener.onNotificationPosted(sbn)

        // Wait to allow any potential background processing
        kotlinx.coroutines.delay(1500)

        val countAfter = transactionRepository.getAll().firstOrNull()?.size ?: 0
        assertEquals("Promo notification must be ignored without creating a transaction", countBefore, countAfter)
    }

    @Test
    fun test_Notification_IgnoresOTP(): Unit = runBlocking {
        val countBefore = transactionRepository.getAll().firstOrNull()?.size ?: 0
        val sbn = createMockSbn(
            packageName = "com.google.android.apps.messaging",
            title = "VK-HDFCBK",
            text = "OTP is 123456"
        )

        listener.onNotificationPosted(sbn)

        // Wait to allow any potential background processing
        kotlinx.coroutines.delay(1500)

        val countAfter = transactionRepository.getAll().firstOrNull()?.size ?: 0
        assertEquals("OTP notification must not create a transaction", countBefore, countAfter)
    }
}
