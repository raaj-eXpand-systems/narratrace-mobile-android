package io.narratrace.android.core.settings

import android.os.Build
import com.google.firebase.messaging.FirebaseMessagingService
import io.narratrace.android.app.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NarratraceMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val container = AppContainer(applicationContext)
            container.sessionManager.restore()
            container.settingsRepository.registerPush(token, Build.VERSION.RELEASE.take(40))
        }
    }
}
