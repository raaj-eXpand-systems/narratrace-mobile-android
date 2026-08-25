package io.narratrace.android.core.customer

import io.narratrace.android.core.network.ApiSuccess
import io.narratrace.android.core.network.NarratraceJson
import kotlinx.serialization.serializer
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CustomerContractTest {
    @Test
    fun `plan intent contract contains only the closed activity action`() {
        val request = NarratraceJson.encodeToString(CustomerActivityRequest("paywall_viewed"))
        assertEquals("{\"action\":\"paywall_viewed\"}", request)
        assertFalse(request.contains("email"))
        assertFalse(request.contains("metadata"))

        val response = NarratraceJson.decodeFromString<CustomerActivityRecorded>("""{"recorded":true}""")
        assertEquals(true, response.recorded)
    }

    @Test
    fun `account contract decodes server formatted storage and capabilities`() {
        val payload = """
            {"data":{"status":"subscription_active","plan":"family","billingCycle":"annual",
            "trialEndsAt":null,"currentPeriodEndsAt":"2027-08-11T20:00:00.000Z","daysRemaining":null,
            "hasAccess":true,"canReadArchive":true,
            "storage":{"usedBytes":1024,"availableBytes":2048,"totalBytes":3072,"usedLabel":"1 KB","availableLabel":"2 KB","totalLabel":"3 KB","usedPercent":33},
            "capabilities":{"captureMemories":true,"createLetters":true,"managePeople":true,"familyCircles":true}},
            "meta":{"apiVersion":"1","requestId":"request-1","supportId":"support-1"}}
        """.trimIndent()

        val account = NarratraceJson.decodeFromString(
            ApiSuccess.serializer(serializer<AccountSummary>()), payload,
        ).data
        assertEquals("Family", account.plan.planLabelForTest())
        assertEquals("2 KB", account.storage.availableLabel)
        assertEquals(33, account.storage.usedPercent)
    }

    @Test
    fun `account contract keeps experience first interview separate from full access`() {
        val payload = """
            {"data":{"status":"invited","plan":null,"billingCycle":null,
            "trialEndsAt":null,"currentPeriodEndsAt":null,"daysRemaining":null,
            "hasAccess":false,"canReadArchive":false,"activated":false,"trialLifecycleStage":null,
            "storage":{"usedBytes":0,"availableBytes":53687091200,"totalBytes":53687091200,"usedLabel":"0 B","availableLabel":"50 GB","totalLabel":"50 GB","usedPercent":0},
            "capabilities":{"captureMemories":true,"createLetters":false,"managePeople":false,"familyCircles":false},
            "experiment":{"cardGateArm":"B","experienceFirst":true,"resourceState":"available"}},
            "meta":{"apiVersion":"1","requestId":"request-exp","supportId":"request-exp"}}
        """.trimIndent()

        val account = NarratraceJson.decodeFromString(
            ApiSuccess.serializer(serializer<AccountSummary>()), payload,
        ).data
        assertFalse(account.hasAccess)
        assertEquals("B", account.experiment?.cardGateArm)
        assertEquals(true, account.experiment?.experienceFirst)
        assertEquals("available", account.experiment?.resourceState)
    }

    @Test
    fun `home contract decodes private memory and attention without identity fields`() {
        val payload = """
            {"data":{"recentMemories":[{"id":"123e4567-e89b-42d3-a456-426614174000","sourceInterviewId":null,"sourceMessageId":null,"title":"A summer afternoon","excerpt":"At the lake","memoryType":"written","visibility":"private","status":"active","pinned":false,"isOwner":true,"updatedAt":"2026-08-11T20:00:00.000Z"}],
            "attention":[{"id":"notice-1","kind":"notification","createdAt":"2026-08-11T20:00:00.000Z","title":"A Letter is ready","body":"Open Narratrace to review it."}],"hasMoreActivity":false},
            "meta":{"apiVersion":"1","requestId":"request-2","supportId":"support-2"}}
        """.trimIndent()

        val home = NarratraceJson.decodeFromString(
            ApiSuccess.serializer(serializer<HomeSummary>()), payload,
        ).data
        assertEquals("A summer afternoon", home.recentMemories.single().title)
        assertEquals("private", home.recentMemories.single().visibility)
        assertEquals("A Letter is ready", home.attention.single().title)
        assertFalse(home.hasMoreActivity)
    }

    @Test
    fun `library contract preserves mosaic ownership and review states`() {
        val payload = """
            {"data":{"mode":"mosaic","memories":[
            {"id":"123e4567-e89b-42d3-a456-426614174000","sourceInterviewId":"223e4567-e89b-42d3-a456-426614174000","sourceMessageId":"323e4567-e89b-42d3-a456-426614174000","title":"A private memory","excerpt":"Review this memory before placing it on your board.","memoryType":"interview","visibility":"private","status":"review_required","pinned":false,"isOwner":true,"updatedAt":"2026-08-11T20:00:00.000Z"},
            {"id":"423e4567-e89b-42d3-a456-426614174000","sourceInterviewId":null,"sourceMessageId":null,"title":"Family recipe","excerpt":"Sunday afternoons","memoryType":"written","visibility":"family","status":"active","pinned":true,"isOwner":false,"updatedAt":"2026-08-10T20:00:00.000Z"}]},
            "meta":{"apiVersion":"1","requestId":"request-3","supportId":"support-3"}}
        """.trimIndent()

        val library = NarratraceJson.decodeFromString(
            ApiSuccess.serializer(serializer<MemoryList>()), payload,
        ).data
        assertEquals("mosaic", library.mode)
        assertEquals("review_required", library.memories.first().status)
        assertFalse(library.memories.last().isOwner)
        assertEquals("family", library.memories.last().visibility)
    }

    @Test
    fun `people list decodes manual and derived records`() {
        val payload = """
            {"data":{"mode":"family","people":[
            {"id":"123e4567-e89b-42d3-a456-426614174000","name":"Maya","relation":"Mother","interviewCount":2,"completedInterviews":1,"letterCount":1,"source":"manual"},
            {"id":"derived:alex%20doe","name":"Alex Doe","relation":null,"interviewCount":1,"completedInterviews":0,"letterCount":0,"source":"derived"}]},
            "meta":{"apiVersion":"1","requestId":"request-4","supportId":"support-4"}}
        """.trimIndent()
        val people = NarratraceJson.decodeFromString(
            ApiSuccess.serializer(serializer<PeopleList>()), payload,
        ).data
        assertEquals("family", people.mode)
        assertEquals("Mother", people.people.first().relation)
        assertEquals("derived", people.people.last().source)
    }

    @Test
    fun `person detail decodes connected content without owner identity`() {
        val payload = """
            {"data":{"kind":"found","person":{"id":"derived:maya","name":"Maya","relation":"Mother","source":"derived",
            "interviews":[{"id":"i-1","status":"complete","createdAt":"2026-08-01T12:00:00.000Z"}],
            "letters":[{"id":"l-1","subject":"For your birthday","unlockAt":"2027-01-01T12:00:00.000Z","delivered":false}],
            "memories":[{"id":"m-1","title":"A private memory","excerpt":"Review required","visibility":"private","updatedAt":"2026-08-11T12:00:00.000Z"}]}},
            "meta":{"apiVersion":"1","requestId":"request-5","supportId":"support-5"}}
        """.trimIndent()
        val detail = NarratraceJson.decodeFromString(
            ApiSuccess.serializer(serializer<PersonDetailResponse>()), payload,
        ).data.person
        assertEquals("Maya", detail.name)
        assertEquals("For your birthday", detail.letters.single().subject)
        assertFalse(detail.letters.single().delivered)
        assertEquals("A private memory", detail.memories.single().title)
    }

    @Test
    fun `written memory creation decodes first response and idempotent replay`() {
        listOf(false, true).forEach { replayed ->
            val payload = """
                {"data":{"kind":"created","id":"42","replayed":$replayed},
                "meta":{"apiVersion":"1","requestId":"request-6","supportId":"support-6"}}
            """.trimIndent()
            val creation = NarratraceJson.decodeFromString(
                ApiSuccess.serializer(serializer<MemoryCreation>()), payload,
            ).data
            assertEquals("created", creation.kind)
            assertEquals("42", creation.id)
            assertEquals(replayed, creation.replayed)
        }
    }

    @Test
    fun `memory mutation response preserves ownership privacy and review state`() {
        val payload = """
            {"data":{"memory":{"id":"123e4567-e89b-42d3-a456-426614174000","sourceInterviewId":null,"sourceMessageId":null,
            "title":"A private memory","excerpt":"Review this memory before placing it on your board.","memoryType":"written",
            "visibility":"private","status":"review_required","pinned":true,"isOwner":true,"updatedAt":"2026-08-11T20:00:00.000Z"}},
            "meta":{"apiVersion":"1","requestId":"request-7","supportId":"support-7"}}
        """.trimIndent()
        val memory = NarratraceJson.decodeFromString(
            ApiSuccess.serializer(serializer<MemoryResponse>()), payload,
        ).data.memory
        assertEquals("review_required", memory.status)
        assertEquals("private", memory.visibility)
        assertEquals(true, memory.pinned)
        assertEquals(true, memory.isOwner)
    }

    @Test fun `search response keeps resource type and authorized destination`() {
        val response = NarratraceJson.decodeFromString<SearchResponse>("""{"query":"maya","results":[{
          "id":"memory:m-1","resourceId":"m-1","kind":"memory","title":"Maya's story","subtitle":"Private Memory"
        }]}""")
        assertEquals("memory", response.results.single().kind)
        assertEquals("m-1", response.results.single().resourceId)
    }

    @Test fun `activity response tolerates progress and announcements`() {
        val page = NarratraceJson.decodeFromString<ActivityPage>("""{"items":[{
          "id":"a-1","kind":"processing","createdAt":"now","title":"Preparing video","state":"running","progress":45
        },{"id":"a-2","kind":"announcement","createdAt":"now","announcement":"Welcome"}],"nextCursor":null}""")
        assertEquals(45, page.items.first().progress)
        assertEquals("Welcome", page.items.last().announcement)
    }
}

private fun String?.planLabelForTest(): String = when (this) {
    "family" -> "Family"
    else -> "Other"
}
