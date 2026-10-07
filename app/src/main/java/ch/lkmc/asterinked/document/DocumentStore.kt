package ch.lkmc.asterinked.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import ch.lkmc.asterinked.ink.InkGeometry
import ch.lkmc.asterinked.ink.InkKind
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

internal data class Draft(
    val source: File,
    val name: String,
    val page: Int = 0,
    val ink: Map<Int, List<InkStroke>> = emptyMap(),
    val savedInk: Map<Int, List<InkStroke>> = emptyMap(),
    /** Where the last export landed; "Save" writes back there without asking. */
    val destination: Uri? = null,
) {
    val dirty: Boolean get() = ink.filterValues { it.isNotEmpty() } != savedInk.filterValues { it.isNotEmpty() }

    /**
     * File name for an exported copy: "Report.pdf" becomes "Report-annotated.pdf", once.
     * The display name comes from another app and also names a file in the share
     * cache, so it is reduced to one short segment without separators or control
     * characters.
     */
    val exportName: String get() {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(PDF_EXTENSION, "")
            .filterNot { it.isISOControl() }
            .take(MAX_BASE_CHARS)
            .trimEnd { it.isHighSurrogate() }
            .ifBlank { DEFAULT_BASE }
        return if (base.endsWith(ANNOTATED_SUFFIX)) "$base.pdf" else "$base$ANNOTATED_SUFFIX.pdf"
    }

    private companion object {
        val PDF_EXTENSION = Regex("(?i)\\.pdf$")
        const val ANNOTATED_SUFFIX = "-annotated"
        const val DEFAULT_BASE = "Document"
        // At most 64 UTF-16 units is at most 192 UTF-8 bytes, well inside the
        // 255-byte file name limit together with the suffix.
        const val MAX_BASE_CHARS = 64
    }
}

/**
 * A fresh private copy of a picked PDF and the SHA-256 of its bytes.
 * [displayName] is null when the provider named nothing; the draft then
 * carries a fallback name.
 */
internal class ImportedPdf(val draft: Draft, val digest: ByteArray, val displayName: String?)

internal class DocumentStore(context: Context, private val draftIo: DraftFileIo = DraftFileIo()) {
    private val resolver = context.contentResolver
    private val directory = File(context.filesDir, "documents").apply { mkdirs() }
    private val draftFile = AtomicFile(File(directory, "draft.json"))
    private val brokenFile = File(directory, "draft.broken.json")

    fun import(uri: Uri): ImportedPdf {
        // A blank name counts as none, so it cannot replace a kept draft's name.
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf { it.isNotBlank() }
        val file = File(directory, "${UUID.randomUUID()}.pdf")
        try {
            val digest = MessageDigest.getInstance(DIGEST_ALGORITHM)
            val input = resolver.openInputStream(uri) ?: throw IOException("Cannot read this PDF.")
            // Hashing on the way through reads the provider's stream only once.
            DigestInputStream(input, digest).use { source -> file.outputStream().use { source.copyTo(it) } }
            return ImportedPdf(Draft(file, name ?: "Document.pdf"), digest.digest(), name)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    /**
     * Whether [imported] holds the same bytes as [source], a PDF imported
     * earlier. A source that is gone or unreadable matches nothing, so the
     * import then replaces it like any other PDF.
     */
    fun sameBytes(imported: ImportedPdf, source: File): Boolean {
        // Sizes differ for almost every other PDF, which spares reading this one.
        if (source.length() != imported.draft.source.length()) return false
        val digest = MessageDigest.getInstance(DIGEST_ALGORITHM)
        try {
            source.forEachBlock { buffer, bytes -> digest.update(buffer, 0, bytes) }
        } catch (error: IOException) {
            return false
        }
        return MessageDigest.isEqual(imported.digest, digest.digest())
    }

    fun writePdf(file: File, uri: Uri) {
        // Export is complete before the provider's destination is opened for writing.
        val output = resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot write to this location.")
        output.use { destination -> file.inputStream().use { it.copyTo(destination) } }
    }

    /** Writes [draft] and prunes every other PDF except those in [keep]. */
    fun saveDraft(draft: Draft, keep: Set<File> = emptySet()) {
        val json = JSONObject()
            .put("source", draft.source.name)
            .put("name", draft.name)
            .put("page", draft.page)
            .put("ink", encodeInk(draft.ink))
            .put("savedInk", encodeInk(draft.savedInk))
            // A destination without a surviving write grant would be a dead
            // "Save" button after the next launch, so it is not recorded.
            .put("destination", draft.destination?.takeIf(::canWrite)?.toString())
        commitDraft(json.toString().toByteArray(Charsets.UTF_8))

        // A committed draft owns one source; failed replacements retain the previous file.
        directory.listFiles()?.filter { it.extension == "pdf" && it != draft.source && it !in keep }?.forEach { it.delete() }
    }

    private fun commitDraft(bytes: ByteArray) {
        val base = draftFile.baseFile
        val backup = File("$base$BACKUP_SUFFIX")
        val pending = File("$base$PENDING_SUFFIX")

        // A legacy AtomicFile backup is authoritative, even if the base is partial.
        if (backup.exists()) renameDraft(backup, base)

        // AtomicFile.finishWrite suppresses commit errors. Stage separately on all
        // APIs so API 29's unchecked backup rename cannot truncate the old draft.
        try {
            draftIo.open(pending).use { output ->
                output.write(bytes)
                draftIo.sync(output)
            }
            renameDraft(pending, base)
        } catch (error: Exception) {
            pending.delete()
            throw error
        }
    }

    private fun renameDraft(source: File, target: File) {
        if (!draftIo.rename(source, target)) throw IOException("Cannot rename $source to $target.")
    }

    fun restore(): Draft? {
        if (!draftFile.baseFile.exists() && !File("${draftFile.baseFile}$BACKUP_SUFFIX").exists()) return null
        val text = draftFile.openRead().bufferedReader().use { it.readText() }
        return try {
            decodeDraft(text)
        } catch (error: Exception) {
            // Content that cannot be decoded never will be: set it aside, once,
            // so the next launch starts clean instead of failing again. Read
            // errors above are not set aside; they may pass.
            brokenFile.delete()
            draftFile.baseFile.renameTo(brokenFile)
            draftFile.delete()
            throw DocumentException(DocumentProblem.DRAFT_UNREADABLE, error)
        }
    }

    private fun decodeDraft(text: String): Draft {
        val json = JSONObject(text)
        val source = File(directory, json.getString("source"))
        if (source.canonicalFile.parentFile != directory.canonicalFile || !source.isFile) throw IOException("The saved PDF is missing.")
        return Draft(source, json.getString("name"), json.getInt("page"), decodeInk(json.getJSONObject("ink")), decodeInk(json.getJSONObject("savedInk")),
            json.optString("destination").takeIf { it.isNotEmpty() }?.let(Uri::parse)?.takeIf(::canWrite))
    }

    private fun canWrite(uri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    private fun encodeInk(ink: Map<Int, List<InkStroke>>): JSONObject = JSONObject().apply {
        for ((page, strokes) in ink) {
            put(page.toString(), JSONArray().apply {
                for (stroke in strokes) {
                    put(JSONObject().put("color", stroke.color).put("width", stroke.width).put("points", JSONArray().apply {
                        for (point in stroke.points) put(JSONArray(listOf(point.x, point.y, point.pressure)))
                    }).apply {
                        // Pen strokes omit the key, so drafts stay readable by older versions.
                        if (stroke.kind != InkKind.PEN) put(KIND_KEY, stroke.kind.name.lowercase())
                    })
                }
            })
        }
    }

    private fun decodeInk(json: JSONObject): Map<Int, List<InkStroke>> = json.keys().asSequence().associate { key ->
        val strokes = json.getJSONArray(key)
        key.toInt() to List(strokes.length()) { index ->
            val stroke = strokes.getJSONObject(index)
            val points = stroke.getJSONArray("points")
            InkStroke(List(points.length()) { pointIndex ->
                val point = points.getJSONArray(pointIndex)
                InkPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat(), point.getDouble(2).toFloat())
            }, stroke.getInt("color"), InkGeometry.strokeWidth(stroke.getDouble("width").toFloat()), kindOf(stroke.optString(KIND_KEY)))
        }
    }

    // Unknown kinds (from a newer version) fall back to pen rather than losing the stroke.
    private fun kindOf(name: String): InkKind = InkKind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: InkKind.PEN

    private companion object {
        const val KIND_KEY = "kind"
        const val BACKUP_SUFFIX = ".bak"
        const val PENDING_SUFFIX = ".new"
        const val DIGEST_ALGORITHM = "SHA-256"
    }
}

/** File primitives kept injectable to prove commit failure cannot prune a source. */
internal open class DraftFileIo {
    open fun open(file: File): FileOutputStream = FileOutputStream(file)
    open fun sync(output: FileOutputStream) = output.fd.sync()
    open fun rename(source: File, target: File): Boolean = source.renameTo(target)
}
