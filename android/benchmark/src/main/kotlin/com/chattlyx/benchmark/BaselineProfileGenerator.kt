package com.chattlyx.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regenerates baseline-profile rules on a reference device (Section 10
 * budgets). The curated `app/baseline-prof.txt` ships today; this test
 * refines it with real execution traces. Run:
 *
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest \
 *       -PchattlyxBenchmark=true \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.chattlyx.benchmark.BaselineProfileGenerator
 *
 * then copy the generated profile from the benchmark output directory into
 * `app/baseline-prof.txt` (see the Macrobenchmark docs for the exact path on
 * your AGP version) and re-run `tools/perf/gen_baseline.py` to keep the
 * curated header.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        // Debug build id; use com.chattlyx.app for signed release runs.
        packageName = "com.chattlyx.app.debug",
        includeInStartupProfile = true,
    ) {
        startActivityAndWait()
        // Idle on the chat list so first-frame composition + paging join the
        // profile. Deliberately no text assertions: the device locale may
        // render translated strings.
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }
}
