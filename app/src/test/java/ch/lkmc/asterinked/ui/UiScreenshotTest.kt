package ch.lkmc.asterinked.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ch.lkmc.asterinked.R
import ch.lkmc.asterinked.ui.EditorScreens.descendants
import ch.lkmc.asterinked.ui.EditorScreens.editing
import ch.lkmc.asterinked.ui.EditorScreens.publish
import ch.lkmc.asterinked.ui.EditorScreens.settle
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * Renders every screen state to build/reports/screens/ so the chrome can be
 * reviewed without a device. Robolectric draws in software with fake system
 * bar insets (24dp status bar, 24dp gesture area).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiScreenshotTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before
    fun startClean() {
        File(app.filesDir, "documents").deleteRecursively()
        app.getSharedPreferences("pen", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun welcome() = shoot("welcome") {}

    @Test @Config(qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun welcomeDark() = shoot("welcome-dark") {}

    @Test @Config(qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun loading() = shoot("loading", settleMillis = 600) { publish(it, EditorState(busy = true)) }

    @Test @Config(qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun editorPhone() = shoot("editor-phone") { publish(it, editing()) }

    @Test @Config(qualifiers = "w360dp-h640dp-port-notnight-xhdpi")
    fun editorSmallPhone() = shoot("editor-small-phone") { publish(it, editing()) }

    @Test @Config(qualifiers = "w320dp-h640dp-port-notnight-xhdpi")
    fun editorNarrowPhone() = shoot("editor-narrow-phone") { publish(it, editing()) }

    @Test @Config(qualifiers = "w680dp-h360dp-land-notnight-xhdpi")
    fun editorCompactLandscape() = shoot("editor-compact-landscape") { publish(it, editing()) }

    @Test @Config(qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun editorPhoneDark() = shoot("editor-phone-dark") { publish(it, editing()) }

    @Test @Config(qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun editorWriting() = shoot("editor-writing", settleMillis = 400) {
        publish(it, editing())
        descendants(it.window.decorView).filterIsInstance<InkPageView>().single()
            .onWritingChanged(WritingState.ACTIVE)
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun editorWritingDark() = shoot("editor-writing-dark", settleMillis = 400) {
        publish(it, editing())
        descendants(it.window.decorView).filterIsInstance<InkPageView>().single()
            .onWritingChanged(WritingState.ACTIVE)
    }

    @Test @Config(qualifiers = "w891dp-h411dp-land-notnight-xhdpi")
    fun editorPhoneLandscape() = shoot("editor-phone-landscape") { publish(it, editing()) }

    @Test @Config(qualifiers = "w800dp-h1280dp-port-notnight-mdpi")
    fun editorTablet() = shoot("editor-tablet") { publish(it, editing()) }

    @Test @Config(qualifiers = "w1280dp-h800dp-land-notnight-mdpi")
    fun editorTabletLandscape() = shoot("editor-tablet-landscape") { publish(it, editing()) }

    @Test @Config(qualifiers = "ar-ldrtl-w411dp-h891dp-port-notnight-xhdpi")
    fun editorRightToLeft() = shoot("editor-rtl") { publish(it, editing()) }

    @Test @Config(qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun editorLargeFont() {
        RuntimeEnvironment.setFontScale(2f)
        shoot("editor-large-font") { publish(it, editing()) }
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun editorSaved() = shoot("editor-saved") {
        publish(it, editing(exported = true, destination = Uri.parse("content://test/saved.pdf")))
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun editorBusy() = shoot("editor-busy") { publish(it, editing().copy(busy = true)) }

    @Test @Config(qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun editorHighlighter() = shoot("editor-highlighter") {
        publish(it, editing())
        click(it, R.string.highlighter)
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun editorError() = shoot("editor-error") {
        publish(it, editing().copy(message = EditorMessage(app.getString(R.string.error_source_unreadable), Tone.ERROR)))
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun editorSuccess() = shoot("editor-success") {
        publish(it, editing(exported = true).copy(exported = android.net.Uri.parse("content://test/saved.pdf")))
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun dialogReplace() = shoot("dialog-replace") {
        publish(it, editing())
        click(it, R.string.open_pdf)
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun dialogReplaceDark() = shoot("dialog-replace-dark") {
        publish(it, editing())
        click(it, R.string.open_pdf)
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun dialogGoToPage() = shoot("dialog-page") {
        publish(it, editing())
        descendants(it.window.decorView).first { view -> view.tooltipText == app.getString(R.string.go_to_page) }.performClick()
    }

    private fun shoot(name: String, settleMillis: Long = 0, arrange: (MainActivity) -> Unit) {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            settle()
            arrange(activity)
            settle()
            if (settleMillis > 0) {
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(settleMillis))
            }
            val metrics = activity.resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.density
            val root = activity.window.decorView
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (STATUS_DP * density).toInt(), 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (NAV_DP * density).toInt()))
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, (STATUS_DP * density).toInt(), 0, (NAV_DP * density).toInt()))
                .build()
            ViewCompat.dispatchApplyWindowInsets(root, insets)
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            settle()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)

            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(image)
            root.draw(canvas)
            drawSystemBars(canvas, width, height, density, activity)
            ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.let { drawDialog(canvas, it, width, height, density) }
            val output = File("build/reports/screens/$name.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun drawDialog(canvas: Canvas, dialog: Dialog, width: Int, height: Int, density: Float) {
        canvas.drawColor(Color.argb(82, 0, 0, 0))
        val decor = dialog.window!!.decorView
        val margin = (DIALOG_MARGIN_DP * density).toInt()
        val maxWidth = minOf(width - margin * 2, (DIALOG_MAX_DP * density).toInt())
        decor.measure(View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        canvas.save()
        canvas.translate((width - decor.measuredWidth) / 2f, (height - decor.measuredHeight) / 2f)
        decor.draw(canvas)
        canvas.restore()
    }

    // A status bar clock and the gesture handle, so inset handling is visible.
    private fun drawSystemBars(canvas: Canvas, width: Int, height: Int, density: Float, context: Context) {
        val night = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val ink = if (night) Color.WHITE else Color.BLACK
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 13 * density }
        canvas.drawText("9:41", 16 * density, 17 * density, paint)
        val handle = RectF(width / 2f - 54 * density, height - 14 * density, width / 2f + 54 * density, height - 10 * density)
        canvas.drawRoundRect(handle, 2 * density, 2 * density, paint)
    }

    private fun click(activity: MainActivity, label: Int) {
        val text = app.getString(label)
        descendants(activity.window.decorView).first { it.contentDescription == text || (it is android.widget.TextView && it.text.toString() == text && it.isClickable) }.performClick()
        settle()
    }

    private companion object {
        const val STATUS_DP = 24
        const val NAV_DP = 24
        const val DIALOG_MARGIN_DP = 40
        const val DIALOG_MAX_DP = 400
    }
}
