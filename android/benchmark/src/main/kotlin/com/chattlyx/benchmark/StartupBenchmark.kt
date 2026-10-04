package com.chattlyx.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold/warm startup benchmarks (Section 10 budgets: < 2 s on a 3 GB mid-range
 * phone). Run on a reference device:
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCold() = startup(StartupMode.COLD)

    @Test
    fun startupWarm() = startup(StartupMode.WARM)

    private fun startup(startupMode: StartupMode) = benchmarkRule.measureRepeated(
        // Debug build id; release id (com.chattlyx.app) for signed runs.
        packageName = "com.chattlyx.app.debug",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = startupMode,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
    }
}
