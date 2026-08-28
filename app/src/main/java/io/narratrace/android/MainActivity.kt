package io.narratrace.android

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import io.narratrace.android.app.AppContainer
import io.narratrace.android.app.NarratraceApp
import io.narratrace.android.core.ui.NarratraceTheme
import io.narratrace.android.core.auth.parseHostedAuthCallback
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        captureInboundLink(intent)
        setContent {
            NarratraceTheme(appearance = container.appearanceStore.load()) { NarratraceApp(container) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureInboundLink(intent)
    }

    private fun captureInboundLink(intent: Intent?) {
        val rawUri = intent?.dataString ?: return
        if (parseHostedAuthCallback(rawUri) != null ||
            rawUri.startsWith("https://www.narratrace.io/mobile/auth/callback/android")
        ) {
            lifecycleScope.launch { container.hostedAuthenticationCoordinator.handleCallback(rawUri) }
            return
        }
        captureInvite(intent)
    }

    private fun captureInvite(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "https" || uri.host != "www.narratrace.io") return
        val kind = when (uri.path) { "/family/accept" -> "family"; "/circles/accept" -> "circle"; else -> return }
        val token = uri.getQueryParameter("token")?.takeIf { it.length in 1..512 } ?: return
        container.pendingInvite = AppContainer.PendingInvite(kind, token)
    }
}
