package io.narratrace.android.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.narratrace.android.core.auth.AuthState
import io.narratrace.android.core.media.FeatureResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun mediaDisplayTitle(title: String, kind: String): String {
    if (kind != "photo") return title
    val value = title.trim()
    val extension = value.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return if (value.isBlank() || value.startsWith("Narratrace-photo-", ignoreCase = true) ||
        extension in setOf("jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "tif", "tiff")) "Photo" else value
}

internal fun mediaSavedDate(value: String): String = runCatching {
    DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault()).format(Instant.parse(value).atZone(ZoneId.systemDefault()))
}.getOrDefault(value.substringBefore('T'))

internal fun thumbnailSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (maxOf(width, height) / sample > 240 && sample < 16384) sample *= 2
    return sample
}

/** LazyColumn owns the request lifetime; previews stay only in account-scoped memory. */
@Composable
internal fun ProtectedPhotoThumbnail(container: AppContainer, mediaId: String) {
    val auth by container.sessionManager.state.collectAsStateWithLifecycle()
    val owner = (auth as? AuthState.Authenticated)?.session?.accountId
    var bitmap by remember(owner, mediaId) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(owner, mediaId) { mutableStateOf(true) }
    LaunchedEffect(owner, mediaId) {
        bitmap = null; loading = true
        if (owner == null) { loading = false; return@LaunchedEffect }
        try {
            val detail = (container.mediaRepository.mediaDetail(mediaId) as? FeatureResult.Success)?.value?.media
            if (detail?.kind == "photo" && detail.rendition == "thumbnail" && detail.playbackUrl != null) {
                val bytes = container.mediaRepository.thumbnail(detail.playbackUrl)
                val decoded = withContext(Dispatchers.Default) {
                    bytes?.let {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(it, 0, it.size, bounds)
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else
                            BitmapFactory.decodeByteArray(it, 0, it.size, BitmapFactory.Options().apply {
                                inSampleSize = thumbnailSampleSize(bounds.outWidth, bounds.outHeight)
                            })
                    }
                }
                coroutineContext.ensureActive()
                if ((container.sessionManager.state.value as? AuthState.Authenticated)?.session?.accountId == owner) bitmap = decoded
            }
        } finally { loading = false }
    }
    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        val preview = bitmap
        if (preview != null) Image(preview.asImageBitmap(), null, Modifier.size(80.dp), contentScale = ContentScale.Fit)
        else if (loading) CircularProgressIndicator(Modifier.size(24.dp))
        else Icon(Icons.Default.PhotoLibrary, null, Modifier.size(32.dp))
    }
}
