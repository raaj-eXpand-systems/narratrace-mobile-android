package io.narratrace.android.core.offline

import android.content.Context
import io.narratrace.android.core.auth.CredentialCipher
import io.narratrace.android.core.network.NarratraceJson
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable data class OfflineLetterDraft(
    val clientDraftId: String = UUID.randomUUID().toString(), val revision: Int = 0,
    val recipientName: String, val subject: String, val body: String, val unlockAt: String? = null,
    val idempotencyKey: String = UUID.randomUUID().toString(),
)

class OfflineDraftStore(private val file: File, private val cipher: CredentialCipher) {
    @Synchronized fun load(): List<OfflineLetterDraft> {
        val encrypted = runCatching { file.takeIf(File::exists)?.readBytes() }.getOrNull() ?: return emptyList()
        val plain = cipher.decrypt(encrypted) ?: return emptyList()
        return runCatching { NarratraceJson.decodeFromString<List<OfflineLetterDraft>>(plain.decodeToString()) }.getOrDefault(emptyList())
    }
    @Synchronized fun save(draft: OfflineLetterDraft): Boolean = write(load().filterNot { it.clientDraftId == draft.clientDraftId } + draft)
    @Synchronized fun remove(id: String): Boolean = write(load().filterNot { it.clientDraftId == id })
    private fun write(value: List<OfflineLetterDraft>): Boolean = runCatching {
        file.parentFile?.mkdirs(); val bytes = cipher.encrypt(NarratraceJson.encodeToString(value).encodeToByteArray()) ?: return false
        val temp = File(file.parentFile, file.name + ".tmp"); temp.writeBytes(bytes); if (!temp.renameTo(file)) { temp.delete(); false } else true
    }.getOrDefault(false)
}

class OnboardingStore(context: Context) {
    private val preferences = context.getSharedPreferences("onboarding.v1", Context.MODE_PRIVATE)
    fun completed() = preferences.getBoolean("completed", false)
    fun complete() = preferences.edit().putBoolean("completed", true).commit()
}
