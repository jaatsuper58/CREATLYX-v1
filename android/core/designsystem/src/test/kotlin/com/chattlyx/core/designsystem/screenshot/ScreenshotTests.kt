package com.chattlyx.core.designsystem.screenshot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.core.designsystem.theme.ChattlyxThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val GOLDEN_DIR = "src/test/screenshots"

/**
 * Golden-image tests. CI runs with -Proborazzi.test.record=true until the
 * first goldens are committed; afterwards switch to verify (CONTRIBUTING.md).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xxhdpi")
class LightScreenshotsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun components_light() {
        composeRule.setContent {
            ChattlyxTheme(themeMode = ChattlyxThemeMode.LIGHT) {
                ScreenshotFixture()
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("$GOLDEN_DIR/components_light.png")
    }

    @Test
    fun emptyState_light() {
        composeRule.setContent {
            ChattlyxTheme(themeMode = ChattlyxThemeMode.LIGHT) {
                EmptyStateFixture()
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("$GOLDEN_DIR/empty_state_light.png")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xxhdpi")
class DarkScreenshotsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun components_dark() {
        composeRule.setContent {
            ChattlyxTheme(themeMode = ChattlyxThemeMode.DARK) {
                ScreenshotFixture()
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("$GOLDEN_DIR/components_dark.png")
    }

    @Test
    fun components_amoled() {
        composeRule.setContent {
            ChattlyxTheme(themeMode = ChattlyxThemeMode.AMOLED) {
                ScreenshotFixture()
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("$GOLDEN_DIR/components_amoled.png")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ldrtl-w400dp-h800dp-xxhdpi")
class RtlLargeFontScreenshotsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun components_rtl_200PercentFont() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2f, fontScale = 2f),
            ) {
                ChattlyxTheme(themeMode = ChattlyxThemeMode.LIGHT) {
                    ScreenshotFixture()
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("$GOLDEN_DIR/components_rtl_font200.png")
    }
}
