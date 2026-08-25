package io.narratrace.android.core.customer

import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.NarratraceApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import io.narratrace.android.core.network.NarratraceJson
import java.net.URLEncoder

@Serializable
data class StorageSummary(
    val usedBytes: Long,
    val availableBytes: Long,
    val totalBytes: Long,
    val usedLabel: String,
    val availableLabel: String,
    val totalLabel: String,
    val usedPercent: Int,
)

@Serializable
data class AccountCapabilities(
    val captureMemories: Boolean,
    val createLetters: Boolean,
    val managePeople: Boolean,
    val familyCircles: Boolean,
)

@Serializable
data class AccountExperiment(
    val cardGateArm: String,
    val experienceFirst: Boolean,
    val resourceState: String? = null,
)

@Serializable
data class DeliveryContact(
    val email: String? = null,
    val verifiedAt: String? = null,
    val reverifyAfter: String? = null,
    val status: String,
)

@Serializable
data class AccountSummary(
    val status: String,
    val plan: String? = null,
    val billingCycle: String? = null,
    val currentPeriodEndsAt: String? = null,
    val hasAccess: Boolean,
    val canReadArchive: Boolean,
    val storage: StorageSummary,
    val capabilities: AccountCapabilities,
    val experiment: AccountExperiment? = null,
    val deliveryContact: DeliveryContact? = null,
)

@Serializable
data class RemoteMemory(
    val id: String,
    val sourceInterviewId: String? = null,
    val sourceMessageId: String? = null,
    val title: String,
    val excerpt: String,
    val memoryType: String,
    val visibility: String,
    val status: String,
    val pinned: Boolean,
    val isOwner: Boolean,
    val updatedAt: String,
)

@Serializable
data class ActivityItem(
    val id: String,
    val kind: String,
    val createdAt: String,
    val title: String? = null,
    val body: String? = null,
    val route: String? = null,
    val state: String? = null,
    val progress: Int? = null,
    val announcement: String? = null,
)

@Serializable
data class HomeSummary(
    val recentMemories: List<RemoteMemory>,
    val attention: List<ActivityItem>,
    val hasMoreActivity: Boolean,
)

@Serializable
data class MemoryList(
    val mode: String,
    val memories: List<RemoteMemory>,
)

@Serializable
data class RemotePerson(
    val id: String,
    val name: String,
    val relation: String? = null,
    val interviewCount: Int,
    val completedInterviews: Int,
    val letterCount: Int,
    val source: String,
)

@Serializable
data class PeopleList(val mode: String, val people: List<RemotePerson>)
@Serializable data class PersonResponse(val person: RemotePerson)
@Serializable private data class PersonInput(val name: String, val relation: String)
@Serializable data class PersonUpdated(val updated: Boolean)
@Serializable data class SearchResult(val id: String, val resourceId: String, val kind: String, val title: String, val subtitle: String)
@Serializable data class SearchResponse(val query: String, val results: List<SearchResult>)
@Serializable data class ActivityPage(val items: List<ActivityItem>, val nextCursor: String? = null)

@Serializable
data class PersonInterview(val id: String, val status: String, val createdAt: String)

@Serializable
data class PersonLetter(val id: String, val subject: String, val unlockAt: String, val delivered: Boolean)

@Serializable
data class PersonMemory(val id: String, val title: String, val excerpt: String, val visibility: String, val updatedAt: String)

@Serializable
data class RemotePersonDetail(
    val id: String,
    val name: String,
    val relation: String? = null,
    val source: String,
    val interviews: List<PersonInterview>,
    val letters: List<PersonLetter>,
    val memories: List<PersonMemory>,
)

@Serializable
data class PersonDetailResponse(val kind: String, val person: RemotePersonDetail)

@Serializable
private data class WrittenMemoryRequest(
    val format: String = "written",
    val title: String,
    val content: String,
)

@Serializable
data class MemoryCreation(val kind: String, val id: String, val replayed: Boolean)

@Serializable
data class MemoryResponse(val memory: RemoteMemory)

@Serializable
internal data class CustomerActivityRequest(val action: String)

@Serializable
data class CustomerActivityRecorded(val recorded: Boolean)

@Serializable
private data class MemoryActionRequest(
    val visibility: String? = null,
    val pinned: Boolean? = null,
    val status: String? = null,
)

interface CustomerGateway {
    suspend fun account(accessToken: String): ApiResult<AccountSummary>
    suspend fun home(accessToken: String): ApiResult<HomeSummary>
    suspend fun memories(accessToken: String): ApiResult<MemoryList>
    suspend fun people(accessToken: String): ApiResult<PeopleList>
    suspend fun person(accessToken: String, id: String): ApiResult<PersonDetailResponse>
    suspend fun createWrittenMemory(
        accessToken: String,
        title: String,
        content: String,
        idempotencyKey: String,
    ): ApiResult<MemoryCreation>
    suspend fun memory(accessToken: String, id: String): ApiResult<MemoryResponse>
    suspend fun updateMemory(
        accessToken: String,
        id: String,
        visibility: String? = null,
        pinned: Boolean? = null,
        status: String? = null,
    ): ApiResult<MemoryResponse>
    suspend fun createPerson(accessToken: String, name: String, relation: String): ApiResult<PersonResponse>
    suspend fun updatePerson(accessToken: String, id: String, name: String, relation: String): ApiResult<PersonUpdated>
    suspend fun search(accessToken: String, query: String): ApiResult<SearchResponse>
    suspend fun activity(accessToken: String): ApiResult<ActivityPage>
    suspend fun recordPlanViewed(accessToken: String): ApiResult<CustomerActivityRecorded>
}

class CustomerApi(private val client: NarratraceApiClient) : CustomerGateway {
    override suspend fun account(accessToken: String): ApiResult<AccountSummary> =
        client.get("/api/v1/account", serializer<AccountSummary>(), accessToken)

    override suspend fun home(accessToken: String): ApiResult<HomeSummary> =
        client.get("/api/v1/home", serializer<HomeSummary>(), accessToken)

    override suspend fun memories(accessToken: String): ApiResult<MemoryList> =
        client.get("/api/v1/memories", serializer<MemoryList>(), accessToken)

    override suspend fun people(accessToken: String): ApiResult<PeopleList> =
        client.get("/api/v1/people", serializer<PeopleList>(), accessToken)

    override suspend fun person(accessToken: String, id: String): ApiResult<PersonDetailResponse> {
        val encoded = encodePathSegment(id)
        return client.get("/api/v1/people/$encoded", serializer<PersonDetailResponse>(), accessToken)
    }

    override suspend fun createWrittenMemory(
        accessToken: String,
        title: String,
        content: String,
        idempotencyKey: String,
    ): ApiResult<MemoryCreation> = client.post(
        path = "/api/v1/memories",
        body = NarratraceJson.encodeToString(WrittenMemoryRequest(title = title, content = content)),
        serializer = serializer<MemoryCreation>(),
        bearer = accessToken,
        idempotencyKey = idempotencyKey,
    )

    override suspend fun memory(accessToken: String, id: String): ApiResult<MemoryResponse> {
        val encoded = encodePathSegment(id)
        return client.get("/api/v1/memories/$encoded", serializer<MemoryResponse>(), accessToken)
    }

    override suspend fun updateMemory(
        accessToken: String,
        id: String,
        visibility: String?,
        pinned: Boolean?,
        status: String?,
    ): ApiResult<MemoryResponse> {
        val encoded = encodePathSegment(id)
        val body = NarratraceJson.encodeToString(
            MemoryActionRequest(visibility = visibility, pinned = pinned, status = status),
        )
        return client.patch("/api/v1/memories/$encoded", body, serializer<MemoryResponse>(), accessToken)
    }
    override suspend fun createPerson(accessToken: String, name: String, relation: String): ApiResult<PersonResponse> = client.post(
        "/api/v1/people", NarratraceJson.encodeToString(PersonInput(name, relation)), serializer<PersonResponse>(), accessToken,
    )
    override suspend fun updatePerson(accessToken: String, id: String, name: String, relation: String): ApiResult<PersonUpdated> = client.patch(
        "/api/v1/people/${encodePathSegment(id)}", NarratraceJson.encodeToString(PersonInput(name, relation)), serializer<PersonUpdated>(), accessToken,
    )
    override suspend fun search(accessToken: String, query: String): ApiResult<SearchResponse> = client.get(
        "/api/v1/search?q=${URLEncoder.encode(query, "UTF-8")}", serializer<SearchResponse>(), accessToken,
    )
    override suspend fun activity(accessToken: String): ApiResult<ActivityPage> = client.get(
        "/api/v1/mobile/activity?limit=50", serializer<ActivityPage>(), accessToken,
    )
    override suspend fun recordPlanViewed(accessToken: String): ApiResult<CustomerActivityRecorded> = client.post(
        "/api/v1/customer-activity",
        NarratraceJson.encodeToString(CustomerActivityRequest(action = "paywall_viewed")),
        serializer<CustomerActivityRecorded>(),
        accessToken,
    )
}

@Suppress("DEPRECATION")
private fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")
