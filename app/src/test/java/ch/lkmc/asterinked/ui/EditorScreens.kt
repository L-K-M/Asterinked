package ch.lkmc.asterinked.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.ink.InkKind
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.robolectric.Shadows.shadowOf
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Puts the real activity into editor states without a PDF: Robolectric cannot
 * run PdfRenderer, so tests publish a state with a drawn stand-in page.
 */
internal object EditorScreens {
    private const val PAGE = 2
    private const val PAGES = 12
    private const val LETTER_WIDTH = 612f
    private const val LETTER_HEIGHT = 792f
    private const val PARAGRAPHS = 4
    private const val LINES = 6
    private const val SETTLE_ROUNDS = 30
    private const val SETTLE_SLEEP_MS = 10L
    private const val RESTORE_TIMEOUT_NS = 10_000_000_000L

    fun publish(activity: MainActivity, state: EditorState) {
        // The draft restore runs on a worker; a state injected before it lands
        // would be overwritten by its result.
        awaitIdle(activity)
        swap(activity, state)
    }

    /** Waits until the model's worker has finished and posted its result. */
    fun awaitIdle(activity: MainActivity) {
        val model = ViewModelProvider(activity)[EditorViewModel::class.java]
        val deadline = System.nanoTime() + RESTORE_TIMEOUT_NS
        while (model.state.value?.busy != false) {
            check(System.nanoTime() < deadline) { "The draft restore did not finish" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(SETTLE_SLEEP_MS)
        }
    }

    /** Puts [state] on screen at once, even a busy one; [publish] must have run first. */
    fun swap(activity: MainActivity, state: EditorState) {
        val model = ViewModelProvider(activity)[EditorViewModel::class.java]
        val field = EditorViewModel::class.java.getDeclaredField("mutableState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(model) as MutableLiveData<EditorState>).value = state
        settle()
    }

    /** Page 3 of 12 with a few notes, exported or not, with a save target or not. */
    fun editing(exported: Boolean = false, destination: Uri? = null): EditorState {
        val ink = mapOf(PAGE to strokes())
        val draft = Draft(File("Quarterly review.pdf"), "Quarterly review.pdf", PAGE, ink, if (exported) ink else emptyMap(), destination)
        val pages = List(PAGES) { PageSpec(0f, 0f, LETTER_WIDTH, LETTER_HEIGHT, 0) }
        return EditorState(draft, pages, page(), busy = false, canUndo = true, canRedo = false)
    }

    // The view model restores on a worker thread; let it finish and post back.
    fun settle() {
        repeat(SETTLE_ROUNDS) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(SETTLE_SLEEP_MS)
        }
    }

    fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    // Handwriting-like waves in the margin, an underline, a circle and a highlight.
    private fun strokes(): List<InkStroke> {
        val wave = (0..60).map { InkPoint(380f + it * 2.6f, 228f + sin(it / 3.0).toFloat() * 7f, 0.4f + (it % 10) / 20f) }
        val underline = (0..50).map { InkPoint(72f + it * 4f, 312f + sin(it / 9.0).toFloat(), 0.7f) }
        val circle = (0..72).map { InkPoint(470f + 40f * cos(it / 11.5).toFloat(), 420f + 22f * sin(it / 11.5).toFloat(), 0.6f) }
        val highlight = listOf(InkPoint(72f, 375f, 1f), InkPoint(320f, 376f, 1f))
        return listOf(
            InkStroke(highlight, Color.rgb(255, 228, 92), 12f, InkKind.HIGHLIGHTER),
            InkStroke(wave, Color.rgb(32, 85, 184), 2.2f),
            InkStroke(underline, Color.rgb(179, 47, 61), 2.2f),
            InkStroke(circle, Color.rgb(179, 47, 61), 2.2f),
        )
    }

    // A plausible document page: a heading, paragraphs and a chart.
    private fun page(): Bitmap {
        val scale = 2f
        val bitmap = Bitmap.createBitmap((LETTER_WIDTH * scale).toInt(), (LETTER_HEIGHT * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(scale, scale)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 60, 60) }
        text.textSize = 22f
        canvas.drawText("Quarterly review", 72f, 110f, text)
        text.textSize = 10f
        val words = "Revenue grew in every region while costs stayed flat; the team shipped three releases and closed most open issues."
        val bar = Paint().apply { color = Color.rgb(120, 150, 200) }
        var y = 160f
        repeat(PARAGRAPHS) { paragraph ->
            repeat(LINES) { line ->
                canvas.drawText(if (line == LINES - 1) words.take(60) else words, 72f, y, text)
                y += 16f
            }
            y += 14f
            if (paragraph != 1) return@repeat

            canvas.drawRect(72f, y, 540f, y + 140f, Paint().apply { color = Color.rgb(236, 240, 246) })
            canvas.drawRect(100f, y + 60f, 160f, y + 130f, bar)
            canvas.drawRect(190f, y + 30f, 250f, y + 130f, bar)
            canvas.drawRect(280f, y + 80f, 340f, y + 130f, bar)
            y += 170f
        }
        return bitmap
    }
}
