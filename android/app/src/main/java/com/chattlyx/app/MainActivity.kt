package com.chattlyx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.chattlyx.app.ui.ChattlyxAppRoot
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity shell (AUTH-01 splash → Compose). Edge-to-edge is enabled
 * here; insets are handled by the design-system scaffolds.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChattlyxAppRoot()
        }
    }
}
