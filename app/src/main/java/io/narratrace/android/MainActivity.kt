package io.narratrace.android

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import io.narratrace.android.app.AppContainer
import io.narratrace.android.app.NarratraceApp
import io.narratrace.android.core.ui.NarratraceTheme

class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        captureInvite(intent)
        setContent {
            NarratraceTheme(appearance = container.appearanceStore.load()) { NarratraceApp(container) }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); captureInvite(intent); recreate() }

    private fun captureInvite(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "https" || uri.host != "www.narratrace.io") return
        val kind = when (uri.path) { "/family/accept" -> "family"; "/circles/accept" -> "circle"; else -> return }
        val token = uri.getQueryParameter("token")?.takeIf { it.length in 1..512 } ?: return
        container.pendingInvite = AppContainer.PendingInvite(kind, token)
    }
}
