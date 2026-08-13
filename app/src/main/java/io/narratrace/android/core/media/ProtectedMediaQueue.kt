package io.narratrace.android.core.media

import io.narratrace.android.core.auth.CredentialCipher
import io.narratrace.android.core.network.NarratraceJson
import java.io.File
import java.security.MessageDigest
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
enum class PendingMediaKind { StandaloneAudio, Photo, StandaloneVideo, InterviewAudio, InterviewVideo }

@Serializable
data class PendingMedia(
    val id: String,
    val kind: PendingMediaKind,
    val encryptedFilename: String,
    val originalFilename: String,
    val mimeType: String,
    val byteCount: Int,
    val sha256: String,
    val interviewId: String? = null,
    val idempotencyKey: String,
    val attempts: Int = 0,
    val uploadUrl: String? = null,
    val serverId: String? = null,
    val chunked: Boolean = false,
)

/** App-private, authenticated-encryption staging. Plaintext is never retained. */
class ProtectedMediaQueue(private val directory: File, private val cipher: CredentialCipher) {
    private val index = File(directory, "queue.bin")

    @Synchronized fun enqueue(
        bytes: ByteArray, kind: PendingMediaKind, filename: String, mimeType: String,
        interviewId: String? = null, idempotencyKey: String = UUID.randomUUID().toString(),
    ): PendingMedia? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES || filename.contains('/') || filename.contains('\\')) return null
        directory.mkdirs()
        val id = UUID.randomUUID().toString()
        val encrypted = cipher.encrypt(bytes) ?: return null
        val blobName = "$id.bin"
        val blob = File(directory, blobName)
        if (!atomicWrite(blob, encrypted)) return null
        val item = PendingMedia(
            id, kind, blobName, filename.take(200), mimeType, bytes.size,
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
            interviewId, idempotencyKey,
        )
        if (!save(items() + item)) { blob.delete(); return null }
        return item
    }

    @Synchronized fun enqueueVideoStream(
        input: InputStream, kind: PendingMediaKind, filename: String, mimeType: String,
        interviewId: String? = null, idempotencyKey: String = UUID.randomUUID().toString(),
    ): PendingMedia? {
        if (kind !in setOf(PendingMediaKind.StandaloneVideo, PendingMediaKind.InterviewVideo) || filename.contains('/') || filename.contains('\\')) return null
        directory.mkdirs()
        val id = UUID.randomUUID().toString()
        val temporary = File(directory, "$id.chunks.tmp").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK_BYTES)
        var total = 0L
        var index = 0
        try {
            while (true) {
                var count = 0
                while (count < buffer.size) {
                    val read = input.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                if (count == 0) break
                total += count
                if (total > MAX_VIDEO_BYTES) { temporary.deleteRecursively(); return null }
                digest.update(buffer, 0, count)
                val encrypted = cipher.encrypt(buffer.copyOf(count)) ?: run { temporary.deleteRecursively(); return null }
                if (!atomicWrite(File(temporary, "%06d.bin".format(index++)), encrypted)) { temporary.deleteRecursively(); return null }
                if (count < buffer.size) break
            }
        } catch (_: Exception) { temporary.deleteRecursively(); return null }
        if (total == 0L || total > Int.MAX_VALUE) { temporary.deleteRecursively(); return null }
        val final = File(directory, "$id.chunks")
        if (!temporary.renameTo(final)) { temporary.deleteRecursively(); return null }
        val item = PendingMedia(
            id, kind, final.name, filename.take(200), mimeType, total.toInt(),
            digest.digest().joinToString("") { "%02x".format(it) }, interviewId, idempotencyKey, chunked = true,
        )
        if (!save(items() + item)) { final.deleteRecursively(); return null }
        return item
    }

    @Synchronized fun items(): List<PendingMedia> {
        val encrypted = runCatching { index.takeIf(File::exists)?.readBytes() }.getOrNull() ?: return emptyList()
        val plain = cipher.decrypt(encrypted) ?: return emptyList()
        return runCatching { NarratraceJson.decodeFromString<List<PendingMedia>>(plain.decodeToString()) }.getOrDefault(emptyList())
    }

    @Synchronized fun read(item: PendingMedia): ByteArray? {
        if (item.chunked) return null
        val file = File(directory, item.encryptedFilename)
        if (file.parentFile != directory || !file.exists()) return null
        val plain = cipher.decrypt(runCatching { file.readBytes() }.getOrNull() ?: return null) ?: return null
        return plain.takeIf { it.size == item.byteCount }
    }

    @Synchronized fun readRange(item: PendingMedia, offset: Int, maximum: Int): ByteArray? {
        if (!item.chunked || offset !in 0 until item.byteCount || maximum <= 0) return null
        val folder = File(directory, item.encryptedFilename)
        if (folder.parentFile != directory || !folder.isDirectory) return null
        val output = ByteArrayOutputStream(minOf(maximum, item.byteCount - offset))
        var position = offset
        while (output.size() < maximum && position < item.byteCount) {
            val chunkIndex = position / CHUNK_BYTES
            val within = position % CHUNK_BYTES
            val file = File(folder, "%06d.bin".format(chunkIndex))
            val plain = cipher.decrypt(runCatching { file.readBytes() }.getOrNull() ?: return null) ?: return null
            val count = minOf(plain.size - within, maximum - output.size(), item.byteCount - position)
            if (count <= 0) return null
            output.write(plain, within, count)
            position += count
        }
        return output.toByteArray()
    }

    @Synchronized fun markAttempt(id: String) = save(items().map { if (it.id == id) it.copy(attempts = it.attempts + 1) else it })

    @Synchronized fun setAuthorization(id: String, uploadUrl: String, serverId: String): Boolean =
        save(items().map { if (it.id == id) it.copy(uploadUrl = uploadUrl, serverId = serverId) else it })

    @Synchronized fun acknowledgeAndRemove(id: String): Boolean {
        val current = items(); val target = current.firstOrNull { it.id == id } ?: return true
        if (!save(current.filterNot { it.id == id })) return false
        val file = File(directory, target.encryptedFilename)
        return if (target.chunked) file.deleteRecursively() || !file.exists() else file.delete() || !file.exists()
    }

    private fun save(value: List<PendingMedia>): Boolean {
        directory.mkdirs()
        val encrypted = cipher.encrypt(NarratraceJson.encodeToString(value).encodeToByteArray()) ?: return false
        return atomicWrite(index, encrypted)
    }

    private fun atomicWrite(file: File, bytes: ByteArray): Boolean = runCatching {
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(file)) { temporary.delete(); false } else true
    }.getOrDefault(false)

    companion object {
        const val MAX_BYTES = 50 * 1024 * 1024
        const val MAX_VIDEO_BYTES = 2_000_000_000L
        const val CHUNK_BYTES = 4 * 1024 * 1024
    }
}
