package io.narratrace.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import io.narratrace.android.app.NarratraceApp
import io.narratrace.android.core.ui.NarratraceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            NarratraceTheme {
                NarratraceApp()
            }
        }
    }
}

