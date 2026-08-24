package io.narratrace.android.core.letters

import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.NarratraceApiClient
import io.narratrace.android.core.network.NarratraceJson
import java.net.URLEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer

@Serializable data class LetterSummary(
    val id: String, val recipientName: String, val subject: String, val unlockAt: String,
    val delivered: Boolean, val recipientVerified: Boolean, val isOwner: Boolean, val createdAt: String,
)
@Serializable data class LetterList(val letters: List<LetterSummary>)
@Serializable data class LetterCreation(val id: String, val replayed: Boolean, val verificationPending: Boolean)
@Serializable data class LetterDetail(
    val id: String, val recipientName: String, val recipientEmail: String? = null, val subject: String,
    val unlockAt: String, val delivered: Boolean, val recipientVerified: Boolean, val createdAt: String,
    val hasAudio: Boolean, val isOwner: Boolean, val sharedDeliveryManaged: Boolean, val canCancel: Boolean,
    val unlocked: Boolean, val body: String? = null,
)
@Serializable data class LetterDetailResponse(val letter: LetterDetail)
@Serializable data class LetterManagement(val kind: String, val recipientEmail: String? = null)
@Serializable private data class LetterAction(val action: String, val recipientEmail: String? = null)
@Serializable private data class CreateLetter(
    val recipientName: String, val recipientEmail: String? = null, val selfDelivery: Boolean,
    val subject: String, val body: String, val deliveryMode: String, val deliverAt: String? = null,
    val deliverTimezone: String? = null, val deliverLocalDatetime: String? = null,
)
@Serializable data class ArtifactDelivery(
    val id: String, val uploadId: Int? = null, val keepsakeBookId: String? = null, val artifactKind: String, val recipientName: String,
    val recipientEmail: String, val selfDelivery: Boolean, val deliverAt: String, val state: String,
    val revokedAt: String? = null, val createdAt: String? = null,
)
@Serializable data class ArtifactDeliveryList(val deliveries: List<ArtifactDelivery>)
@Serializable data class Revocation(val revoked: Boolean)
@Serializable data class ArtifactDeliveryCreation(val kind: String, val delivery: ArtifactDelivery)
@Serializable private data class ArtifactDeliveryInput(
    val uploadId: String? = null, val keepsakeBookId: String? = null, val recipientName: String, val recipientEmail: String? = null,
    val selfDelivery: Boolean, val deliveryMode: String, val deliverAt: String? = null,
    val deliverTimezone: String? = null, val deliverLocalDatetime: String? = null,
)

class LettersApi(private val client: NarratraceApiClient) {
    suspend fun letters(token: String): ApiResult<LetterList> = client.get("/api/v1/letters", serializer<LetterList>(), token)
    suspend fun letter(id: String, token: String): ApiResult<LetterDetailResponse> = client.get("/api/v1/letters/${segment(id)}", serializer<LetterDetailResponse>(), token)
    suspend fun create(
        recipientName: String, recipientEmail: String?, selfDelivery: Boolean, subject: String, body: String,
        deliveryMode: String, deliverAt: String?, timezone: String?, localDateTime: String?, key: String, token: String,
    ): ApiResult<LetterCreation> = client.post(
        "/api/v1/letters", NarratraceJson.encodeToString(CreateLetter(
            recipientName, recipientEmail, selfDelivery, subject, body, deliveryMode, deliverAt, timezone, localDateTime,
        )), serializer<LetterCreation>(), token, key,
    )
    suspend fun manage(id: String, action: String, email: String?, token: String): ApiResult<LetterManagement> = client.patch(
        "/api/v1/letters/${segment(id)}", NarratraceJson.encodeToString(LetterAction(action, email)), serializer<LetterManagement>(), token,
    )
    suspend fun delete(id: String, token: String): ApiResult<LetterManagement> = client.delete("/api/v1/letters/${segment(id)}", serializer<LetterManagement>(), token)
    suspend fun deliveries(token: String): ApiResult<ArtifactDeliveryList> = client.get("/api/v1/artifact-deliveries", serializer<ArtifactDeliveryList>(), token)
    suspend fun revokeDelivery(id: String, token: String): ApiResult<Revocation> = client.delete("/api/v1/artifact-deliveries?id=${segment(id)}", serializer<Revocation>(), token)
    suspend fun createDelivery(
        uploadId: String, name: String, email: String?, self: Boolean, mode: String,
        at: String?, timezone: String?, local: String?, token: String,
    ): ApiResult<ArtifactDeliveryCreation> = client.post(
        "/api/v1/artifact-deliveries", NarratraceJson.encodeToString(ArtifactDeliveryInput(uploadId = uploadId, recipientName = name, recipientEmail = email, selfDelivery = self, deliveryMode = mode, deliverAt = at, deliverTimezone = timezone, deliverLocalDatetime = local)),
        serializer<ArtifactDeliveryCreation>(), token,
    )
    suspend fun createKeepsakeDelivery(
        keepsakeBookId: String, name: String, email: String?, self: Boolean, mode: String,
        at: String?, timezone: String?, local: String?, token: String,
    ): ApiResult<ArtifactDeliveryCreation> = client.post(
        "/api/v1/artifact-deliveries", NarratraceJson.encodeToString(ArtifactDeliveryInput(keepsakeBookId = keepsakeBookId, recipientName = name, recipientEmail = email, selfDelivery = self, deliveryMode = mode, deliverAt = at, deliverTimezone = timezone, deliverLocalDatetime = local)),
        serializer<ArtifactDeliveryCreation>(), token,
    )
}

@Suppress("DEPRECATION") private fun segment(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
