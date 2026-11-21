---
name: macrobenchmark-baseline-profiles
description: Use this skill when configuring, executing, or analyzing Jetpack Macrobenchmark tests (Cold/Warm/Hot startup, TTID, TTFD, frame timing/jank) and generating Baseline Profiles to optimize AOT compilation and startup memory in PaisaPal KMP.
license: Apache-2.0
metadata:
  author: PaisaPal Engineering
  keywords:
    - macrobenchmark
    - baseline profiles
    - startup timing
    - TTID
    - TTFD
    - cold start
    - warm start
    - perfetto
    - memory
    - RSS
---

# PaisaPal Macrobenchmark & Baseline Profiles Skill

This skill governs end-to-end performance benchmarking and Ahead-of-Time (AOT) startup optimization for PaisaPal KMP.

---

## 1. Core Principles & Metrics

### 1.1 Startup Modes
* **Cold Start (`StartupMode.COLD`)**: Process killed, zero state in memory. Measures full initialization: `Application.onCreate()`, Koin DI graph assembly, SQLCipher driver open, Compose hierarchy inflation, first frame draw.
* **Warm Start (`StartupMode.WARM`)**: Activity recreated or process warm; skips `Application.onCreate()`.
* **Hot Start (`StartupMode.HOT`)**: Activity brought to foreground from backstack; near instantaneous (<100ms).

### 1.2 Display Timers
* **TTID (Time to Initial Display)**: Time from process launch to the first frame render (tracked automatically by `StartupTimingMetric()`).
* **TTFD (Time to Full Display)**: Time until the primary dashboard is populated with decrypted database content. Call `reportFullyDrawn()` or use Compose `ReportDrawnWhen` to signal TTFD.

### 1.3 Percentile Evaluation
* Benchmark runs must execute **minimum 10-20 iterations** (`iterations = 10`).
* Never rely on average (`mean`); optimize for **`p50`** (median typical launch) and watch **`p90` / `p99`** to detect lock contention or garbage collection stalls.

---

## 2. Benchmark Architecture (`:benchmark` Module)

PaisaPal isolates benchmarks in `:benchmark` targeting non-debuggable release builds (`benchmark` build type).

### 2.1 Startup Benchmark Implementation
```kotlin
package com.cashbuddy.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupColdNoCompilation() = benchmarkRule.measureRepeated(
        packageName = "com.cashbuddy",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 10
    ) {
        startActivityAndWait { intent ->
            intent.putExtra("skip_permission_prompts", true)
        }
    }

    @Test
    fun startupColdBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = "com.cashbuddy",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 10
    ) {
        startActivityAndWait { intent ->
            intent.putExtra("skip_permission_prompts", true)
        }
    }
}
```

### 2.2 Baseline Profile Generation
Generates Ahead-of-Time compilation paths to eliminate JIT pauses on initial installation:

```kotlin
package com.cashbuddy.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = baselineRule.collect(
        packageName = "com.cashbuddy"
    ) {
        pressHome()
        startActivityAndWait { intent ->
            intent.putExtra("skip_permission_prompts", true)
        }

        // Exercise critical paths: scroll main feed & navigate
        val startX = device.displayWidth / 2
        val startY = (device.displayHeight * 0.7).toInt()
        val endY = (device.displayHeight * 0.3).toInt()

        device.swipe(startX, startY, startX, endY, 20)
        device.waitForIdle()
        device.swipe(startX, endY, startX, startY, 20)
        device.waitForIdle()
    }
}
```

---

## 3. Execution Commands via Gradle & CLI

### Run Baseline Profile Generator:
```bash
./gradlew :benchmark:connectedFdroidBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.cashbuddy.benchmark.BaselineProfileGenerator
```

Generated rules are saved to:
`androidApp/src/main/baselineProfiles/baseline-prof.txt`

### Run Macrobenchmarks:
```bash
./gradlew :benchmark:connectedFdroidBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.cashbuddy.benchmark.StartupBenchmark
```

---

## 4. Measuring Memory Footprint (`RSS + Swap`) During Benchmarks

During startup benchmarks, observe process memory footprint to prevent Low Memory Killer (LMKD) termination:

```bash
# 1. Quick RSS & Swap snapshot
adb shell "grep -E '^(VmRSS|VmSwap|RssAnon|RssFile):' /proc/\$(pidof com.cashbuddy)/status"

# 2. Detailed smaps rollup
adb shell cat /proc/$(pidof com.cashbuddy)/smaps_rollup
```

* High `RssFile` indicates heavy DEX / asset mapping; optimize with `r8-analyzer` class shrinking and method merging.
* High `RssAnon` indicates early JVM/Native heap allocations; defer non-critical Koin singletons.
