package com.cashbuddy

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.ocr.OcrEngine
import com.cashbuddy.ocr.OcrResult
import com.cashbuddy.screenshot.ScreenshotHandler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShareIntentTest : KoinComponent {

    private lateinit var context: Context
    private lateinit var transactionRepository: TransactionRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        transactionRepository = get()
    }

    @Test
    fun test_ShareTextIntent_IsParsed(): Unit = runBlocking {
        val sharedText = "Paid ₹500 to Swiggy. UPI Ref: 1234567890"

        val sendIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sharedText)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        ActivityScenario.launch<MainActivity>(sendIntent).use { scenario ->
            // Allow asynchronous pipeline ingestion to execute
            var foundTx: Transaction? = null
            val deadline = System.currentTimeMillis() + 6000L
            while (System.currentTimeMillis() < deadline) {
                // Check both pending and all transactions
                val pendingList = transactionRepository.getPending().firstOrNull().orEmpty()
                foundTx = pendingList.find { it.rawText.contains("1234567890") || it.merchant.contains("Swiggy", ignoreCase = true) }
                if (foundTx != null) break

                val allList = transactionRepository.getAll().firstOrNull().orEmpty()
                foundTx = allList.find { it.rawText.contains("1234567890") || it.merchant.contains("Swiggy", ignoreCase = true) }
                if (foundTx != null) break

                kotlinx.coroutines.delay(100)
            }

            assertNotNull("Shared text transaction should be parsed and recorded into repository", foundTx)
            assertEquals("Transaction amount should match parsed amount", 500.0, foundTx!!.amount, 0.01)
            assertTrue("Merchant should be Swiggy", foundTx.merchant.contains("Swiggy", ignoreCase = true))
        }
    }

    @Test
    fun test_ShareImageIntent_IsHandled(): Unit = runBlocking {
        // 1. Mock the OCR Engine to return a structured transaction string
        val mockOcr = mockk<OcrEngine>(relaxed = true)
        every { mockOcr.isAvailable } returns true
        coEvery { mockOcr.recognizeText(any()) } returns OcrResult(
            text = "Paid ₹350 to Starbucks. UPI Ref: 9876543210",
            success = true
        )

        // Inject the mocked OCR engine into Koin
        loadKoinModules(module {
            single<OcrEngine> { mockOcr }
        })

        // 2. Prepare a cached screenshot file
        val cacheFolder = File(context.cacheDir, "screenshots").apply { mkdirs() }
        val fakeImageFile = File(cacheFolder, "test_receipt_${System.currentTimeMillis()}.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        }

        // Process screenshot via ScreenshotHandler
        val handler = ScreenshotHandler(context)
        val createdIds = handler.processScreenshots(listOf(fakeImageFile))

        // 3. Verify OCR result is parsed and transaction is processed
        assertTrue("Screenshot handler should create at least one transaction from OCR", createdIds.isNotEmpty())

        val txId = createdIds.first()
        val createdTx = transactionRepository.getById(txId).firstOrNull()
        assertNotNull("Created transaction must exist in the database", createdTx)
        assertEquals("Parsed amount must match mocked OCR output", 350.0, createdTx!!.amount, 0.01)
        assertTrue("Merchant must match Starbucks from OCR output", createdTx.merchant.contains("Starbucks", ignoreCase = true))

        // Also test the ActivityScenario launch lifecycle with ACTION_SEND image/*
        val imageIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(fakeImageFile))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        ActivityScenario.launch<MainActivity>(imageIntent).use { scenario ->
            assertNotNull("MainActivity should handle the image share intent without crashing", scenario)
        }
    }
}
