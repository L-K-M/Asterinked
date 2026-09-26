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
}

internal class DocumentStore(context: Context) {
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
        val output = draftFile.startWrite()
        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            draftFile.finishWrite(output)
        } catch (error: Exception) {
            draftFile.failWrite(output)
            throw error
        }
        // A committed draft owns one source; failed replacements retain the previous file.
        directory.listFiles()?.filter { it.extension == "pdf" && it != draft.source }?.forEach { it.delete() }
    }

    fun restore(): Draft? {
        if (!draftFile.baseFile.exists() && !File("${draftFile.baseFile}.bak").exists()) return null
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
    }
}
