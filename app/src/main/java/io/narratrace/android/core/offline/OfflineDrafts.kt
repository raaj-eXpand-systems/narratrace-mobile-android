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
    val recipientEmail: String? = null, val selfDelivery: Boolean? = null,
    val circleId: String? = null, val circleMemberEmail: String? = null,
    val deliveryMode: String? = null, val deliveryTimezone: String? = null,
    val deliveryLocalDatetime: String? = null,
    // Every recovered draft requires an explicit review; old records lack recipient choices.
    val requiresDeliveryReview: Boolean = true,
    val ownerAccountId: String? = null,
)

class OfflineDraftStore(private val file: File, private val cipher: CredentialCipher, private val owner: (() -> String?)? = null) {
    private fun belongsToAccount(draft: OfflineLetterDraft, ownerId: String?): Boolean = owner == null || (ownerId != null && draft.ownerAccountId == ownerId)
    private fun draftsFor(ownerId: String?): List<OfflineLetterDraft> = allDrafts().filter { belongsToAccount(it, ownerId) }
    @Synchronized fun load(): List<OfflineLetterDraft> {
        val ownerId = owner?.invoke()
        return draftsFor(ownerId)
    }
    private fun allDrafts(): List<OfflineLetterDraft> {
        val encrypted = runCatching { file.takeIf(File::exists)?.readBytes() }.getOrNull() ?: return emptyList()
        val plain = cipher.decrypt(encrypted) ?: return emptyList()
        return runCatching { NarratraceJson.decodeFromString<List<OfflineLetterDraft>>(plain.decodeToString()) }.getOrDefault(emptyList())
    }
    @Synchronized fun save(draft: OfflineLetterDraft): Boolean {
        val ownerId = owner?.invoke()
        if (owner != null && (ownerId == null || (draft.ownerAccountId != null && draft.ownerAccountId != ownerId))) return false
        return write(draftsFor(ownerId).filterNot { it.clientDraftId == draft.clientDraftId } + draft.copy(ownerAccountId = ownerId), ownerId)
    }
    @Synchronized fun remove(id: String): Boolean {
        val ownerId = owner?.invoke()
        return write(draftsFor(ownerId).filterNot { it.clientDraftId == id }, ownerId)
    }
    @Synchronized fun purgeAccountData(): Boolean {
        val ownerId = owner?.invoke()
        if (owner != null && ownerId == null) return false
        if (!write(emptyList(), ownerId)) return false
        if (allDrafts().isNotEmpty()) return true
        val fileRemoved = runCatching { !file.exists() || file.delete() }.getOrDefault(false)
        val keyDestroyed = (cipher as? io.narratrace.android.core.auth.KeystoreCredentialCipher)?.destroyKey() ?: true
        return fileRemoved && keyDestroyed
    }
    private fun write(value: List<OfflineLetterDraft>, ownerId: String?): Boolean = runCatching {
        if (owner != null && ownerId == null) return false
        if (value.any { !belongsToAccount(it, ownerId) }) return false
        val merged = allDrafts().filterNot { belongsToAccount(it, ownerId) } + value
        file.parentFile?.mkdirs(); val bytes = cipher.encrypt(NarratraceJson.encodeToString(merged).encodeToByteArray()) ?: return false
        val temp = File(file.parentFile, file.name + ".tmp"); temp.writeBytes(bytes); if (!temp.renameTo(file)) { temp.delete(); false } else true
    }.getOrDefault(false)
}

class OnboardingStore(context: Context) {
    private val preferences = context.getSharedPreferences("onboarding.v1", Context.MODE_PRIVATE)
    // Presentation preferences only: these never grant access or store captured content.
    fun completed() = preferences.getBoolean("introduction.v2", false)
    fun complete(newUser: Boolean = true) = preferences.edit()
        .putBoolean("introduction.v2", true)
        .putBoolean("newUserJourney.pending", newUser).commit()
    fun finishJourney() = preferences.edit().putBoolean("newUserJourney.pending", false).commit()
}
