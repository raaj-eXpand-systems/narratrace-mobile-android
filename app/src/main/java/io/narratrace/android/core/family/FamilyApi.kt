package io.narratrace.android.core.family

import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.NarratraceApiClient
import io.narratrace.android.core.network.NarratraceJson
import java.net.URLEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer

@Serializable data class Family(val id: String, val name: String? = null, val myRole: String)
@Serializable data class FamilyMember(val id: String, val email: String, val role: String, val status: String, val isCurrentUser: Boolean)
@Serializable data class FamilySummary(val family: Family? = null, val members: List<FamilyMember>)
@Serializable private data class NameInput(val name: String)
@Serializable private data class InvitationInput(val email: String, val role: String)
@Serializable data class Invitation(val email: String, val role: String, val status: String, val delivered: Boolean)
@Serializable data class InvitationResponse(val invitation: Invitation)
@Serializable private data class RoleInput(val role: String)
@Serializable data class Updated(val updated: Boolean)
@Serializable private data class Decision(val token: String, val accept: Boolean)
@Serializable data class FamilyDecision(val accepted: Boolean, val familyId: String)

@Serializable data class Circle(val id: String, val name: String, val description: String? = null, val role: String, val createdAt: String)
@Serializable data class CircleList(val circles: List<Circle>)
@Serializable data class CircleResponse(val circle: Circle)
@Serializable data class CircleMember(val id: String, val memberEmail: String, val displayName: String? = null, val status: String, val invitedAt: String, val joinedAt: String? = null)
@Serializable data class CircleMemory(val id: String, val subjectName: String, val subjectRelation: String? = null, val lifeDecade: Int? = null, val messageCount: Int, val narrative: String? = null, val createdAt: String, val updatedAt: String)
@Serializable data class CircleLetter(val id: String, val subject: String, val recipientName: String, val body: String, val hasAudio: Boolean, val unlockAt: String? = null, val createdAt: String)
@Serializable data class CircleDetail(val circle: Circle, val members: List<CircleMember>, val sharedInterviewIds: List<String>, val sharedMemories: List<CircleMemory>, val deliveredLetters: List<CircleLetter>)
@Serializable private data class CircleInput(val name: String, val description: String? = null)
@Serializable private data class CircleAction(val action: String, val email: String? = null, val displayName: String? = null, val interviewIds: List<String>? = null)
@Serializable data class CircleMutation(val invited: Boolean? = null, val removed: Boolean? = null, val deleted: Boolean? = null, val sharedInterviewIds: List<String>? = null)
@Serializable data class CircleDecision(val accepted: Boolean, val circleId: String)

class FamilyApi(private val client: NarratraceApiClient) {
    suspend fun family(token: String): ApiResult<FamilySummary> = client.get("/api/v1/family", serializer<FamilySummary>(), token)
    suspend fun createFamily(name: String, token: String): ApiResult<FamilySummary> = client.post("/api/v1/family", NarratraceJson.encodeToString(NameInput(name)), serializer<FamilySummary>(), token)
    suspend fun invite(email: String, role: String, token: String): ApiResult<InvitationResponse> = client.post("/api/v1/family/invitations", NarratraceJson.encodeToString(InvitationInput(email, role)), serializer<InvitationResponse>(), token)
    suspend fun updateMember(email: String, role: String, token: String): ApiResult<Updated> = client.patch("/api/v1/family/members/${segment(email)}", NarratraceJson.encodeToString(RoleInput(role)), serializer<Updated>(), token)
    suspend fun removeMember(email: String, token: String): ApiResult<Updated> = client.delete("/api/v1/family/members/${segment(email)}", serializer<Updated>(), token)
    suspend fun decideFamily(tokenValue: String, accept: Boolean, token: String): ApiResult<FamilyDecision> = client.post("/api/v1/family/accept", NarratraceJson.encodeToString(Decision(tokenValue, accept)), serializer<FamilyDecision>(), token)
    suspend fun circles(token: String): ApiResult<CircleList> = client.get("/api/v1/circles", serializer<CircleList>(), token)
    suspend fun createCircle(name: String, description: String?, token: String): ApiResult<CircleResponse> = client.post("/api/v1/circles", NarratraceJson.encodeToString(CircleInput(name, description)), serializer<CircleResponse>(), token)
    suspend fun circle(id: String, token: String): ApiResult<CircleDetail> = client.get("/api/v1/circles/${segment(id)}", serializer<CircleDetail>(), token)
    suspend fun circleAction(id: String, action: String, email: String?, displayName: String?, ids: List<String>?, token: String): ApiResult<CircleMutation> = client.post("/api/v1/circles/${segment(id)}", NarratraceJson.encodeToString(CircleAction(action, email, displayName, ids)), serializer<CircleMutation>(), token)
    suspend fun deleteCircle(id: String, token: String): ApiResult<CircleMutation> = client.delete("/api/v1/circles/${segment(id)}", serializer<CircleMutation>(), token)
    suspend fun decideCircle(tokenValue: String, accept: Boolean, token: String): ApiResult<CircleDecision> = client.post("/api/v1/circles/accept", NarratraceJson.encodeToString(Decision(tokenValue, accept)), serializer<CircleDecision>(), token)
}
@Suppress("DEPRECATION") private fun segment(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
