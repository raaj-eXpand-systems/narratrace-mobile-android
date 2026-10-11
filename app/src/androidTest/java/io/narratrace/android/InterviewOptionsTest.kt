package io.narratrace.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.narratrace.android.core.ui.NarratraceAppearance
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.narratrace.android.app.InterviewOptionsDetails
import io.narratrace.android.core.ui.NarratraceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InterviewOptionsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun both_modes_have_the_same_actions_and_switching_updates_selection() {
        var completed = 0; var deleted = 0; var back = 0
        var mode by mutableStateOf("self")
        compose.setContent {
            NarratraceTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InterviewOptionsDetails("Synthetic storyteller", "myself", mode, false, true,
                        { mode = it }, { completed++ }, { deleted++ }, { back++ })
                }
            }
        }
        for (label in listOf("Someone else is here", "Recording myself")) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNodeWithText(label).assertIsSelected()
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            java.io.File(context.cacheDir, "interview-options-$mode.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            for (action in listOf("Mark complete", "Delete interview", "Back to interviews")) {
                compose.onNodeWithText(action).performScrollTo().assertIsDisplayed().performClick()
            }
        }
        compose.runOnIdle { assertEquals(2, completed); assertEquals(2, deleted); assertEquals(2, back) }
    }
    @Test fun large_text_and_themes_keep_actions_reachable_on_small_screens() {
        var appearance by mutableStateOf(NarratraceAppearance.UpcomingPreview)
        var mode by mutableStateOf("self")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                NarratraceTheme(appearance = appearance) {
                    Column(Modifier.size(320.dp, 560.dp).verticalScroll(rememberScrollState())) {
                        InterviewOptionsDetails("Synthetic storyteller with a long display name", "myself", mode, false, false, { mode = it }, {}, {}, {})
                    }
                }
            }
        }
        for (theme in listOf(NarratraceAppearance.UpcomingPreview, NarratraceAppearance.Daylight, NarratraceAppearance.Lamplight)) {
            compose.runOnIdle { appearance = theme }
            compose.onNodeWithText("Mark complete").performScrollTo().assertIsNotEnabled()
            for (label in listOf("Back to interviews", "Delete interview", "Someone else is here", "Recording myself")) {
                val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
                node.assertHeightIsAtLeast(48.dp)
                org.junit.Assert.assertTrue(node.fetchSemanticsNode().boundsInRoot.width <= compose.onRoot().fetchSemanticsNode().boundsInRoot.width)
            }
            compose.onNodeWithText("Someone else is here").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithText("Recording myself").performScrollTo().performClick().assertIsSelected()
        }
    }
    @Test fun busy_options_prevent_interrupting_capture_or_upload() {
        compose.setContent { NarratraceTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InterviewOptionsDetails("Synthetic storyteller", null, "together", true, true, {}, {}, {}, {})
            }
        } }
        for (action in listOf("Mark complete", "Delete interview", "Back to interviews", "Someone else is here", "Recording myself")) {
            compose.onNodeWithText(action).performScrollTo().assertIsNotEnabled()
        }
    }
}
