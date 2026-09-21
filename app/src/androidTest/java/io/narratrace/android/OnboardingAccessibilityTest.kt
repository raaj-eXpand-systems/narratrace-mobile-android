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
import io.narratrace.android.app.ReceptionDestinations
import io.narratrace.android.app.OnboardingScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun trial_access_actions_preserve_story_and_refresh_without_purchase_steering() {
        var opens = 0; var refreshes = 0
        compose.setContent { io.narratrace.android.core.ui.NarratraceTheme {
            androidx.compose.foundation.layout.Column {
                io.narratrace.android.app.TrialAccessActions(true, { opens++ }, { refreshes++ })
            }
        } }
        compose.onNodeWithText("Your complimentary interview is complete. You can still open your existing story.").assertIsDisplayed()
        compose.onNodeWithText("Open existing story").performClick()
        compose.onNodeWithText("Refresh access").performClick()
        compose.runOnIdle { assertEquals(1, opens); assertEquals(1, refreshes) }
        compose.onNodeWithText("View plans").assertDoesNotExist()
    }

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
        compose.onNodeWithContentDescription("Narratrace").assertIsDisplayed()
        compose.onNodeWithText("STORIES THAT MATTER.").assertIsDisplayed()
        compose.onNodeWithText("Welcome to\nNarratrace").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("Welcome to\nNarratrace").assertIsDisplayed()
    }

    @Test fun launch_tagline_remains_reachable_at_large_font_scale() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)
            ) { NarratraceLaunch { } }
        }
        compose.onNodeWithText("STORIES THAT MATTER.").performScrollTo().assertIsDisplayed()
    }

    @Test fun compact_wordmark_keeps_trademark_with_accessible_name() {
        compose.setContent { io.narratrace.android.app.NarratraceWordmark() }
        compose.onNodeWithText("NARRATRACE™").assertIsDisplayed()
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

    @Test fun reception_opens_destinations_only_after_selection() {
        var destination: CustomerTab? = null
        compose.setContent {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                ReceptionDestinations { destination = it }
            }
        }
        compose.onNodeWithText("Explore Narratrace").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, destination) }
        compose.onNodeWithText("Stories").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(CustomerTab.Stories, destination) }
        compose.onNodeWithText("Capture").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(CustomerTab.Capture, destination) }
        compose.onNodeWithText("More").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(CustomerTab.More, destination) }
    }
}
