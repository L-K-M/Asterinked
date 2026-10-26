package ch.lkmc.asterinked.ui

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import ch.lkmc.asterinked.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

// The licences screen reads the generated res/raw/aboutlibraries.json, so these
// fail when a bundled component or one of the texts its licence requires is
// missing from the APK's notices.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class LicensesActivityTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun theStartScreenOpensTheLicences() {
        File(app.filesDir, "documents").deleteRecursively()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            repeat(SETTLE_ROUNDS) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(SETTLE_SLEEP_MS)
            }
            val label = app.getString(R.string.open_source_licenses)
            descendants(activity.window.decorView).filterIsInstance<Button>().single { it.isShown && it.text == label }.performClick()
            assertEquals(LicensesActivity::class.java.name, shadowOf(activity).nextStartedActivity?.component?.className)
        }
    }

    @Test fun listsEveryBundledComponent() = licences { rows, _ ->
        for (name in BUNDLED) assertTrue("$name is listed", rows.any { it.startsWith(name) })
    }

    @Test fun showsTheNoticesTheLicencesRequire() = licences { rows, open ->
        for ((name, texts) in REQUIRED_TEXTS) {
            val shown = open(rows.first { it.startsWith(name) })
            for (text in texts) assertTrue("$name shows '$text'", shown.contains(text))
        }
    }

    /** Runs [check] on the screen's rows and a way to open one row's texts. */
    private fun licences(check: (rows: List<String>, open: (String) -> String) -> Unit) {
        Robolectric.buildActivity(LicensesActivity::class.java).setup().use { controller ->
            val list = descendants(controller.get().window.decorView).filterIsInstance<ListView>().single()
            val adapter = list.adapter
            // The header row carries no data.
            val rows = (0 until adapter.count).mapNotNull { position -> adapter.getItem(position)?.let { position to it.toString() } }
            check(rows.map { it.second }) { row ->
                val position = rows.first { it.second == row }.first
                list.onItemClickListener!!.onItemClick(list, null, position, adapter.getItemId(position))
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                descendants(dialog.window!!.decorView).filterIsInstance<TextView>().joinToString("\n") { it.text.toString() }
            }
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    private companion object {
        const val SETTLE_ROUNDS = 50
        const val SETTLE_SLEEP_MS = 10L

        // Row titles start with the component name.
        val BUNDLED = listOf(
            "PdfBox-Android", "Bouncy Castle Provider", "Kotlin Stdlib", "kotlinx-coroutines-core", "Activity",
            "Liberation Sans Regular", "Adobe Core 14 font metrics", "Adobe CMap resources", "Adobe Glyph List",
            "Unicode Character Database files", "Material Symbols",
        )

        // NOTICE files (Apache-2.0 4(d)) and the copyright lines MIT, BSD and OFL texts must carry.
        val REQUIRED_TEXTS = mapOf(
            "PdfBox-Android" to listOf("Apache PDFBox", "Copyright (c) 2002-2007, www.pdfbox.org"),
            "Bouncy Castle Provider" to listOf("The Legion of the Bouncy Castle Inc."),
            "Kotlin Stdlib" to listOf("JetBrains s.r.o", "Stephen Colebourne"),
            "Liberation Sans Regular" to listOf("SIL OPEN FONT LICENSE Version 1.1", "Copyright (c) 2012 Red Hat, Inc."),
            "Adobe Core 14 font metrics" to listOf("AFM files are not distributed without this file"),
            "Material Symbols" to listOf("Google", "Apache License"),
        )
    }
}
