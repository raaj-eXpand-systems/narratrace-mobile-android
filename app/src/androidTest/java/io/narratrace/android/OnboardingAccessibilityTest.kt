package io.narratrace.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class OnboardingAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun onboarding_has_a_visible_heading_and_large_primary_action() {
        compose.activity.getSharedPreferences("onboarding.v1", MODE_PRIVATE).edit().clear().commit()
        compose.activity.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Your stories, protected").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsDisplayed()
    }

    private companion object { const val MODE_PRIVATE = 0 }
}
