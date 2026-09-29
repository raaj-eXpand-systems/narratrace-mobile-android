package io.narratrace.android

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.narratrace.android.app.RelationshipMapScreen
import io.narratrace.android.core.customer.RemotePerson
import io.narratrace.android.core.ui.NarratraceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RelationshipMapAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    @Test fun map_has_zoom_and_accessible_list_that_opens_selected_person() {
        var selected: String? = null
        val people = listOf(RemotePerson("fixture", "Maya", "mother", 1, 1, 0, "manual"))
        compose.setContent { NarratraceTheme { RelationshipMapScreen(people, Modifier, {}, {}, { selected = it.id }) } }
        compose.onNodeWithContentDescription("Zoom out").performClick()
        compose.onNodeWithText("77%").assertIsDisplayed()
        compose.onNodeWithText("List").performClick()
        compose.onNodeWithText("Older generations").assertIsDisplayed()
        compose.onNodeWithText("Maya").performClick()
        compose.runOnIdle { assertEquals("fixture", selected) }
    }
}
