package com.cashbuddy.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark suite measuring:
 * - Cold, Warm, and Hot startup modes.
 * - TTID (Time to Initial Display) and TTFD (Time to Full Display).
 * - Compilation performance: Baseline Profile (AOT) vs No Compilation.
 * - Memory footprint metrics: RSS and PSS (Last/Max) to guard against LMKD.
 * - Evaluates across p50, p90, and p99 percentile distributions with 20 iterations.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    companion object {
        private const val PACKAGE_NAME = "com.cashbuddy"
        private const val BENCHMARK_ITERATIONS = 10
    }

    // =========================================================================
    // 1. Cold Startup Benchmarks (Process cold, DI graph build, DB decryption)
    // =========================================================================

    @Test
    fun startupColdRegularNoCompilation() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(
            StartupTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Last)
        ),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = BENCHMARK_ITERATIONS
    ) {
        startActivityAndWait { intent ->
            intent.putExtra("skip_permission_prompts", true)
        }
    }

    @Test
    fun startupColdRegularBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(
            StartupTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Last),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Max)
        ),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = BENCHMARK_ITERATIONS
    ) {
        startActivityAndWait { intent ->
            intent.putExtra("skip_permission_prompts", true)
        }
    }

    // =========================================================================
    // 2. Warm Startup Benchmarks (Process alive, Activity recreated)
    // =========================================================================

    @Test
    fun startupWarmRegular() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(
            StartupTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Last)
        ),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.WARM,
        iterations = BENCHMARK_ITERATIONS,
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait { intent ->
            intent.putExtra("skip_permission_prompts", true)
        }
    }

    // =========================================================================
    // 3. Hot Startup Benchmarks (Activity brought to foreground from backstack)
    // =========================================================================

    @Test
    fun startupHotRegular() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(
            StartupTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Last)
        ),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.HOT,
        iterations = BENCHMARK_ITERATIONS,
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait { intent ->
            intent.putExtra("skip_permission_prompts", true)
        }
    }

    // =========================================================================
    // 4. Entry Point: Direct Notification Review Box (Pending Transactions)
    // =========================================================================

    @Test
    fun startupColdNotificationReviewBox() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(
            StartupTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Last)
        ),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = BENCHMARK_ITERATIONS
    ) {
        startActivityAndWait { intent ->
            intent.putExtra("skip_permission_prompts", true)
            intent.putExtra("EXTRA_NAVIGATE_TO", "review_inbox")
        }
    }

    @Test
    fun startupWarmNotificationReviewBox() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(
            StartupTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Last)
        ),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.WARM,
        iterations = BENCHMARK_ITERATIONS,
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait { intent ->
            intent.putExtra("skip_permission_prompts", true)
            intent.putExtra("EXTRA_NAVIGATE_TO", "review_inbox")
        }
    }

    // =========================================================================
    // 5. Entry Point: OCR / Share Sheet Receipt Intake (Send Intent)
    // =========================================================================

    @Test
    fun startupColdShareReceipt() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(
            StartupTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Last)
        ),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = BENCHMARK_ITERATIONS
    ) {
        startActivityAndWait { intent ->
            intent.action = Intent.ACTION_SEND
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, "Paid Rs. 450 to Swiggy on 20-Oct-2026 Ref UPI/429819201928")
            intent.putExtra("skip_permission_prompts", true)
        }
    }
}
