package io.narratrace.android.core.network

import io.narratrace.android.core.auth.SessionManager
import io.narratrace.android.core.auth.AuthState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable

@Serializable data class DeletionChallenge(val token: String, val method: String, val maskedEmail: String, val expiresInSeconds: Int)
@Serializable internal data class DeletionChallengeInput(val resource: String)

/** A prompt belongs to one operation generation. No code is stored after dismissal. */
class DeletionVerification(private val sessions: SessionManager) {
    data class Prompt(val method: String, val maskedEmail: String, val error: String?)
    private val mutablePrompt = MutableStateFlow<Prompt?>(null)
    val prompt = mutablePrompt.asStateFlow()
    private val lock = Mutex()
    private var answer: CompletableDeferred<String?>? = null
    suspend fun request(challenge: DeletionChallenge, bearer: String, error: String?): String? {
        val owner = sessions.captureOperationLease() ?: return null
        if ((sessions.state.value as? AuthState.Authenticated)?.session?.accessToken != bearer || !lock.tryLock()) return null
        val pending = CompletableDeferred<String?>()
        try {
            answer = pending
            mutablePrompt.value = Prompt(challenge.method, challenge.maskedEmail, error)
            val code = pending.await()
            return code?.takeIf { sessions.isCurrent(owner) && it.matches(Regex("[0-9]{6}")) }
        } finally { answer = null; mutablePrompt.value = null; lock.unlock() }
    }
    fun submit(code: String) { if (code.matches(Regex("[0-9]{6}"))) answer?.complete(code) }
    fun cancel() { answer?.complete(null) }
}
