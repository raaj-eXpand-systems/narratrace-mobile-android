package io.narratrace.android.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.narratrace.android.R
import io.narratrace.android.core.customer.AccountSummary

@Composable
internal fun NarratraceWordmark() {
    Text("Narratrace", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif,
        color = MaterialTheme.colorScheme.primary)
}

/** Bundled photographic artwork remains available before sign-in and while offline. */
@Composable
internal fun WelcomeArtwork(resource: Int, modifier: Modifier = Modifier) {
    val painter = painterResource(resource)
    val ratio = painter.intrinsicSize.width / painter.intrinsicSize.height
    val screenLimit = (LocalConfiguration.current.screenHeightDp.dp * 0.42f).coerceIn(240.dp, 420.dp)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val imageWidth = minOf(maxWidth, minOf(maxHeight, screenLimit) * ratio)
        Image(painter, contentDescription = null,
            modifier = Modifier.width(imageWidth).aspectRatio(ratio),
            contentScale = ContentScale.Fit)
    }
}

/** The logo introduces a normal app launch; browser callbacks enter directly. */
@Composable
internal fun NarratraceLaunch(showOnLaunch: Boolean = true, content: @Composable () -> Unit) {
    var introducing by rememberSaveable { mutableStateOf(showOnLaunch) }
    LaunchedEffect(Unit) { delay(1_200); introducing = false }
    if (introducing) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.narratrace_brand_mark), "Narratrace logo",
                Modifier.weight(1f, fill = false).widthIn(max = 280.dp).aspectRatio(1f, matchHeightConstraintsFirst = true), contentScale = ContentScale.Fit)
            Text("NARRATRACE", style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Serif)
            Text("── • ──", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.primary)
            Text("CAPTURE TODAY.\nTREASURE FOREVER.", textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    } else content()
}

@Composable
internal fun OnboardingScreen(modifier: Modifier = Modifier, replay: Boolean = false, complete: (Boolean) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = page > 0) { page-- }
    val titles = listOf("Welcome to\nNarratrace", "Some memories\ndisappear quietly.", "More than photos.", "Your memories belong\nto your family.", "Let’s preserve something meaningful.")
    WelcomePage(modifier, page) {
        val photograph = listOf(R.drawable.welcome_family, R.drawable.welcome_recipe, R.drawable.welcome_family, R.drawable.welcome_garden, R.drawable.welcome_lake)[page]
        WelcomeArtwork(photograph)
        if (page == 3) Icon(Icons.Default.Shield, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(titles[page], Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
        Text("── • ──", color = MaterialTheme.colorScheme.primary)
        when (page) {
            0 -> Text("Every family has stories. Some are told every year. Others are remembered only once.\n\nKeep them for generations to come.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            1 -> {
                Text("A favorite recipe.\nA childhood story.\nThe way someone laughed.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                Text("Keep these moments in your family’s own words.", textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Serif, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            2 -> {
                Text("Narratrace helps preserve:")
                listOf(
                    Triple(Icons.Default.Mic, "Voices", "Stories told in their own words"),
                    Triple(Icons.Default.MenuBook, "Stories", "Life lessons and cherished moments"),
                    Triple(Icons.Default.PhotoLibrary, "Photos", "Moments captured to be remembered"),
                    Triple(Icons.Default.Videocam, "Videos", "Moments, smiles and milestones"),
                    Triple(Icons.Default.MailOutline, "Future messages", "Deliver love for tomorrow and beyond"),
                ).forEach { (icon, title, body) ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                            Column { Text(title, style = MaterialTheme.typography.titleSmall); Text(body, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            3 -> Text("You decide what is preserved.\n\nYou decide who can access it.\n\nNothing is shared automatically.\n\nNarratrace exists to help protect your family’s legacy.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            4 -> {
                Text("Whether it’s a story from today or a memory from decades ago, Narratrace is ready whenever you are.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Button({ if (page < titles.lastIndex) page++ else complete(true) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (page == 0) "Begin" else if (page < titles.lastIndex) "Continue" else if (replay) "Return to Settings" else "Start my journey")
        }
        TextButton({ complete(if (replay) false else page == 0) }, Modifier.fillMaxWidth()) {
            Text(if (replay) "Close introduction" else if (page == 0) "Skip introduction" else "I already have an account")
        }
        if (page > 0) TextButton({ page-- }) { Text("Back") }
    }
}

@Composable
private fun WelcomePage(modifier: Modifier = Modifier, step: Int = 0, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    Box(modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxSize().verticalScroll(scroll).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}

/** Reached only inside the authenticated lifecycle and required-legal gates. */
@Composable
internal fun NewUserWelcome(complete: (CustomerTab) -> Unit) {
    var purpose by rememberSaveable { mutableIntStateOf(-1) }
    BackHandler(enabled = purpose >= 0) { purpose = -1 }
    WelcomePage(step = purpose) {
        NarratraceWordmark()
        if (purpose < 0) {
            WelcomeArtwork(R.drawable.welcome_garden)
            Text("What brings you here today?", Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
            Text("Choose the option that best fits you. You can always explore the other options later.", textAlign = TextAlign.Center)
            listOf(
                Triple(Icons.Default.People, "Preserve someone I love", "Record stories, conversations and memories together."),
                Triple(Icons.Default.Person, "Preserve my own story", "Leave memories for the people you love."),
                Triple(Icons.Default.MailOutline, "I received a memory", "Explore the memory shared with you."),
            ).forEachIndexed { index, (icon, title, body) ->
                OutlinedCard(onClick = { purpose = index }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                        Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            TextButton({ complete(CustomerTab.Home) }) { Text("Explore Reception first") }
        } else {
            WelcomeArtwork(R.drawable.welcome_family)
            Text(if (purpose == 2) "We’re glad you’re here." else "Wonderful.", Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Serif)
            Text(if (purpose == 2) "A memory is a meaningful gift. Explore your Wall and the memories you have access to."
                else "Every family has stories worth remembering. Let’s capture your first one together.", textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge)
            Button({ complete(if (purpose == 2) CustomerTab.Wall else CustomerTab.Capture) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (purpose == 2) "Explore my memories" else "Begin")
            }
            TextButton({ purpose = -1 }) { Text("Back") }
        }
    }
}

@Composable
internal fun ReceptionWelcome() {
    NarratraceWordmark()
    WelcomeArtwork(R.drawable.welcome_garden)
    Text("Welcome home.", Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Serif)
    Text("What would you like to remember today?", style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun FirstMemoryWelcome(openCapture: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your story starts with one memory.", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
            Text("A familiar voice. A favorite photograph. Something you never want to forget.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(openCapture, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Capture a memory") }
        }
    }
}

@Composable
internal fun ReceptionDestinations(openCapture: () -> Unit, openLibrary: () -> Unit, openPeople: () -> Unit, openMore: () -> Unit) {
    Text("Quick actions", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
    val actions = listOf(
        Triple("Capture", Icons.Default.Mic, openCapture),
        Triple("Media", Icons.Default.PhotoLibrary, openLibrary),
        Triple("People", Icons.Default.People, openPeople),
        Triple("More", Icons.Default.Settings, openMore),
    )
    actions.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { (label, icon, action) ->
                OutlinedCard(onClick = action, modifier = Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Product capacity comes from the existing server projection, never byte quotas. */
internal fun accountAllowanceLabels(account: AccountSummary): List<Pair<String, String>> = buildList {
    account.productionArchives.forEach { archive ->
        archive.photographs?.let { add(archive.subjectName to "Photos: ${"%,d".format(it.remaining)} of ${"%,d".format(it.granted)} remaining") }
        archive.audioSeconds?.let { add(archive.subjectName to "Voice recording: ${it.remaining.durationAllowanceLabel()} remaining") }
        if (account.capabilities.captureVideo) archive.videoSeconds?.let {
            add(archive.subjectName to "Video: ${it.remaining.durationAllowanceLabel()} remaining")
        }
    }
    account.productionPools.audioSeconds?.takeIf { it.remaining > 0 }?.let { add("Shared allowance" to "Voice recording: ${it.remaining.durationAllowanceLabel()} remaining") }
    account.productionPools.videoSeconds?.takeIf { account.capabilities.captureVideo && it.remaining > 0 }?.let { add("Shared allowance" to "Video: ${it.remaining.durationAllowanceLabel()} remaining") }
    add("Letters" to if (account.capabilities.createLetters) "Available with your plan" else "Not included in your current access")
    if (!account.capabilities.captureVideo) add("Video" to "Not included in your current access")
}

@Composable
internal fun AccountAllowances(account: AccountSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Room for your memories", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
        accountAllowanceLabels(account).forEach { (title, value) ->
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun CaptureChoices(
    audioEnabled: Boolean, photoEnabled: Boolean, videoEnabled: Boolean, writingEnabled: Boolean,
    recordAudio: () -> Unit, addPhoto: () -> Unit, addVideo: () -> Unit, write: () -> Unit,
) {
    val labels = listOf("Record story", "Add photos", "Add video", "Write memory")
    val icons = listOf(Icons.Default.Mic, Icons.Default.PhotoLibrary, Icons.Default.Videocam, Icons.Default.Edit)
    val enabled = listOf(audioEnabled, photoEnabled, videoEnabled, writingEnabled)
    val actions = listOf(recordAudio, addPhoto, addVideo, write)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(0..1, 2..3).forEach { indices ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                indices.forEach { index ->
                    OutlinedCard(onClick = actions[index], enabled = enabled[index], modifier = Modifier.weight(1f)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 92.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(icons[index], null, Modifier.size(32.dp), tint = if (enabled[index]) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(labels[index], textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}


/** The same labelled, non-interactive initial illustration as the web Media page. */
@Composable
internal fun LibraryPhotoIllustration() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Every old photo has a story it hasn't finished telling.", style = MaterialTheme.typography.headlineSmall)
        Text("Upload a family photo to preserve it and add your own memory. If you enable optional photo insights, Nia can describe the scene, identify the era, and suggest helpful tags.")
        Image(painterResource(R.drawable.library_sample),
            contentDescription = "A woman holding the Dance to Your Film Favourites and Saturday Night Fever vinyl records while a man places a record on a record player, with a cassette player in the background",
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentScale = ContentScale.Fit)
        Text( "Illustrative example only. This sample photo will disappear as soon as you upload your own photo.", style = MaterialTheme.typography.titleMedium)
        Text("Example Nia insight", style = MaterialTheme.typography.titleMedium)
        Text("Two adults are sharing music indoors. One holds the readable Dance to Your Film Favourites and Saturday Night Fever record sleeves while the other places a vinyl record on a turntable; a cassette radio sits behind them.")
        Text("Era clue: Likely late 1970s to early 1980s, based on the records, cassette radio, clothing, and photographic print. This is an estimate.")
        Text("vinyl records · turntable · music at home · cassette radio · 1970s–1980s")
        Text("Nia would ask: Who are the two people, and what do you remember about the records they chose?")

    }
}

internal fun shouldShowLibraryIllustration(media: io.narratrace.android.core.media.FeatureResult<io.narratrace.android.core.media.MediaList>?): Boolean =
    media is io.narratrace.android.core.media.FeatureResult.Success && media.value.media.size < 1000 && media.value.media.none { it.kind == "photo" }


@Composable
internal fun ThemeChoices(selected: io.narratrace.android.core.ui.NarratraceAppearance, choose: (io.narratrace.android.core.ui.NarratraceAppearance) -> Unit) {
    val primary = listOf(io.narratrace.android.core.ui.NarratraceAppearance.System, io.narratrace.android.core.ui.NarratraceAppearance.Light, io.narratrace.android.core.ui.NarratraceAppearance.Dark)
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        primary.forEach { appearance ->
            OutlinedButton(onClick = { choose(appearance) }, modifier = Modifier.fillMaxWidth().semantics { this.selected = selected == appearance }) {
                Text(appearance.displayName + if (selected == appearance) " ✓" else "")
            }
        }
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().semantics { this.selected = selected !in primary }) {
                Text(if (selected in primary) "More" else "More: ${selected.displayName} ✓")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                io.narratrace.android.core.ui.NarratraceAppearance.entries.filterNot { it in primary }.forEach { appearance ->
                    DropdownMenuItem(text = { Text(appearance.displayName + if (selected == appearance) " ✓" else "") }, onClick = { expanded = false; choose(appearance) })
                }
            }
        }
    }
}
