package io.narratrace.android

import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import io.narratrace.android.app.NarratraceLaunch
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.narratrace.android.app.CustomerTab
import io.narratrace.android.app.NewUserWelcome
import io.narratrace.android.app.OnboardingScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun library_sample_and_insight_remain_readable_in_phone_scroll() {
        compose.setContent {
            io.narratrace.android.core.ui.NarratraceTheme {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    io.narratrace.android.app.LibraryPhotoIllustration()
                }
            }
        }
        compose.onNodeWithText("Illustrative example only. This sample photo will disappear as soon as you upload your own photo.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nia would ask: Who are the two people, and what do you remember about the records they chose?").performScrollTo().assertIsDisplayed()
    }

    @Test fun logo_precedes_photographic_welcome_on_launch() {
        compose.mainClock.autoAdvance = false
        compose.setContent { NarratraceLaunch { OnboardingScreen { } } }
        compose.onNodeWithContentDescription("Narratrace logo").assertIsDisplayed()
        compose.onNodeWithText("Welcome to\nNarratrace").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("Welcome to\nNarratrace").assertIsDisplayed()
    }

    @Test fun introduction_distinguishes_new_and_returning_journeys() {
        var newUser: Boolean? = null
        compose.setContent { OnboardingScreen { newUser = it } }
        compose.onNodeWithText("Welcome to\nNarratrace").assertIsDisplayed()
        compose.onNodeWithText("Begin").performScrollTo().performClick()
        compose.onNodeWithText("Some memories\ndisappear quietly.").assertIsDisplayed()
        compose.onNodeWithText("I already have an account").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(false, newUser) }
    }

    @Test fun full_introduction_enters_new_user_journey() {
        var newUser: Boolean? = null
        compose.setContent { OnboardingScreen { newUser = it } }
        compose.onNodeWithText("Begin").performScrollTo().performClick()
        repeat(3) { compose.onNodeWithText("Continue").performScrollTo().performClick() }
        compose.onNodeWithText("Start my journey").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, newUser) }
    }

    @Test fun skipping_introduction_still_requests_new_user_onboarding() {
        var newUser: Boolean? = null
        compose.setContent { OnboardingScreen { newUser = it } }
        compose.onNodeWithText("Skip introduction").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, newUser) }
    }

    @Test fun new_user_selects_purpose_before_opening_capture() {
        var destination: CustomerTab? = null
        compose.setContent { NewUserWelcome { destination = it } }
        compose.onNodeWithText("What brings you here today?").assertIsDisplayed()
        compose.onNodeWithText("Preserve someone I love").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(null, destination) }
        compose.onNodeWithText("Wonderful.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Begin").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(CustomerTab.Capture, destination) }
    }

    @Test fun recipient_journey_opens_shared_memory_wall_instead_of_capture() {
        var destination: CustomerTab? = null
        compose.setContent { NewUserWelcome { destination = it } }
        compose.onNodeWithText("I received a memory").performScrollTo().performClick()
        compose.onNodeWithText("Explore my memories").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(CustomerTab.Wall, destination) }
    }
}
