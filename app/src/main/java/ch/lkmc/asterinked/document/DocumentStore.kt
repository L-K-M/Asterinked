package ch.lkmc.asterinked.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import ch.lkmc.asterinked.ink.InkKind
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

internal data class Draft(
    val source: File,
    val name: String,
    val page: Int = 0,
    val ink: Map<Int, List<InkStroke>> = emptyMap(),
    val savedInk: Map<Int, List<InkStroke>> = emptyMap(),
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

internal class DocumentStore(context: Context, private val draftIo: DraftFileIo = DraftFileIo()) {
    private val resolver = context.contentResolver
    private val directory = File(context.filesDir, "documents").apply { mkdirs() }
    private val draftFile = AtomicFile(File(directory, "draft.json"))

    fun import(uri: Uri): Draft {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Document.pdf"
        val file = File(directory, "${UUID.randomUUID()}.pdf")
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Cannot read this PDF.")
            input.use { source -> file.outputStream().use { source.copyTo(it) } }
            return Draft(file, name)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    fun writePdf(file: File, uri: Uri) {
        // Export is complete before the provider's destination is opened for writing.
        val output = resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot write to this location.")
        output.use { destination -> file.inputStream().use { it.copyTo(destination) } }
    }

    fun saveDraft(draft: Draft) {
        val json = JSONObject()
            .put("source", draft.source.name)
            .put("name", draft.name)
            .put("page", draft.page)
            .put("ink", encodeInk(draft.ink))
            .put("savedInk", encodeInk(draft.savedInk))
        commitDraft(json.toString().toByteArray(Charsets.UTF_8))

        // A committed draft owns one source; failed replacements retain the previous file.
        directory.listFiles()?.filter { it.extension == "pdf" && it != draft.source }?.forEach { it.delete() }
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
        val json = JSONObject(draftFile.openRead().bufferedReader().use { it.readText() })
        val source = File(directory, json.getString("source"))
        require(source.canonicalFile.parentFile == directory.canonicalFile && source.isFile) { "The saved PDF is missing." }
        return Draft(source, json.getString("name"), json.getInt("page"), decodeInk(json.getJSONObject("ink")), decodeInk(json.getJSONObject("savedInk")))
    }

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
            }, stroke.getInt("color"), stroke.getDouble("width").toFloat(), kindOf(stroke.optString(KIND_KEY)))
        }
    }

    // Unknown kinds (from a newer version) fall back to pen rather than losing the stroke.
    private fun kindOf(name: String): InkKind = InkKind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: InkKind.PEN

    private companion object {
        const val KIND_KEY = "kind"
        const val BACKUP_SUFFIX = ".bak"
        const val PENDING_SUFFIX = ".new"
    }
}

/** File primitives kept injectable to prove commit failure cannot prune a source. */
internal open class DraftFileIo {
    open fun open(file: File): FileOutputStream = FileOutputStream(file)
    open fun sync(output: FileOutputStream) = output.fd.sync()
    open fun rename(source: File, target: File): Boolean = source.renameTo(target)
}
