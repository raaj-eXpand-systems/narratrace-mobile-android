package io.narratrace.android.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.narratrace.android.core.customer.RemotePerson
import kotlin.math.roundToInt

internal fun relationshipGeneration(relation: String?): String = when (relation.orEmpty().trim().lowercase()) {
    "grandparent", "grandmother", "grandfather", "parent", "mother", "father", "aunt", "uncle" -> "Older generations"
    "child", "daughter", "son", "grandchild", "granddaughter", "grandson", "niece", "nephew" -> "Younger generations"
    "self", "myself", "me", "spouse", "partner", "sibling", "sister", "brother", "cousin" -> "Your generation"
    else -> "Other relationships"
}

@Composable
internal fun RelationshipMapScreen(people: List<RemotePerson>, modifier: Modifier, close: () -> Unit, addPerson: () -> Unit, openPerson: (RemotePerson) -> Unit) {
    val groups = remember(people) {
        val grouped = people.groupBy { relationshipGeneration(it.relation) }
        listOf("Older generations", "Your generation", "Younger generations", "Other relationships").mapNotNull { title -> grouped[title]?.let { title to it.sortedBy { person -> person.name.lowercase() } } }
    }
    var list by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    var counts by remember { mutableStateOf(emptyMap<String, Int>()) }
    BackHandler(onBack = close)
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { IconButton(close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to People") }; Text("Relationship map", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall); TextButton({ list = !list }) { Text(if (list) "Map" else "List") } }
        if (people.isEmpty()) {
            Text("Your relationship map is ready for its first person.")
            Button(addPerson) { Text("Add a person") }
        } else if (list) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                groups.forEach { (title, members) ->
                    item { Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge) }
                    items(members, key = { it.id }) { person -> PersonMapCard(person, Modifier.fillMaxWidth(), openPerson) }
                }
            }
        } else BoxWithConstraints(Modifier.weight(1f)) {
            val viewportWidth = maxWidth
            val width = (groups.size * 280 + 32).dp
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ zoom = (zoom / 1.3f).coerceAtLeast(.25f) }, Modifier.semantics { contentDescription = "Zoom out" }) { Text("−") }
                    Text("${(zoom * 100).roundToInt()}%", Modifier.padding(top = 12.dp))
                    TextButton({ zoom = (zoom * 1.3f).coerceAtMost(2.5f) }, Modifier.semantics { contentDescription = "Zoom in" }) { Text("+") }
                    TextButton({ zoom = (viewportWidth.value / width.value).coerceIn(.25f, 1f) }) { Text("Fit map") }
                }
                Box(Modifier.weight(1f).horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
                    Column(Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(maxWidth = androidx.compose.ui.unit.Constraints.Infinity, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                        layout((placeable.width * zoom).roundToInt(), (placeable.height * zoom).roundToInt()) { placeable.placeRelativeWithLayer(0, 0) { scaleX = zoom; scaleY = zoom; transformOrigin = TransformOrigin(0f, 0f) } }
                    }.width(width).padding(16.dp)) {
                        Text("Your people", Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        val lineColor = MaterialTheme.colorScheme.outline
                        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                            groups.indices.forEach { index -> drawLine(lineColor, Offset(size.width / 2, 0f), Offset((index + .5f) * size.width / groups.size, size.height), strokeWidth = 2.dp.toPx()) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            groups.forEach { (title, members) -> Column(Modifier.width(264.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton({ collapsed = if (title in collapsed) collapsed - title else collapsed + title }, Modifier.fillMaxWidth()) { Text("$title · ${members.size} ${if (title in collapsed) "+" else "−"}") }
                                if (title !in collapsed) {
                                    val count = counts[title] ?: 4
                                    members.take(count).forEach { person -> PersonMapCard(person, Modifier.fillMaxWidth(), openPerson) }
                                    if (count < members.size) TextButton({ counts = counts + (title to count + 6) }) { Text("Show more (${members.size - count})") }
                                }
                            } }
                        }
                    }
                }
                Text("Use the zoom controls and drag to explore. Tap a branch to expand or collapse. Tap a person to open their story.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PersonMapCard(person: RemotePerson, modifier: Modifier, open: (RemotePerson) -> Unit) {
    OutlinedCard(onClick = { open(person) }, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(person.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(person.relation?.takeIf { it.isNotBlank() } ?: "Relationship not set")
            Text("${person.interviewCount} interviews · ${person.letterCount} Letters", style = MaterialTheme.typography.bodySmall)
        }
    }
}
