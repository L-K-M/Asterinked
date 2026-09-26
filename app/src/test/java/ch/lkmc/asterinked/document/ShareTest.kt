package ch.lkmc.asterinked.document

import android.net.Uri
import androidx.core.content.FileProvider
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun exportNamesGainTheSuffixOnce() {
        fun name(original: String) = Draft(File("x.pdf"), original).exportName
        assertEquals("Report-annotated.pdf", name("Report.pdf"))
        assertEquals("Report-annotated.pdf", name("Report.PDF"))
        assertEquals("Report-annotated.pdf", name("Report-annotated.pdf"))
        assertEquals("Notes-annotated.pdf", name("Notes"))
    }

    @Test fun sharedCopyCarriesInkUnderTheProviderPathAndReplacesTheLastOne() {
        val service = DocumentService(app)
        val source = File(app.filesDir, "documents/share-source.pdf").apply {
            parentFile!!.mkdirs()
            PDDocument().use { document ->
                document.addPage(PDPage())
                document.save(this)
            }
        }
        val draft = Draft(source, "Review.pdf", ink = mapOf(0 to listOf(InkStroke(listOf(InkPoint(10f, 10f, 1f), InkPoint(80f, 40f, 1f)), 0, 2f))))

        val first = service.share(draft)
        val second = service.share(draft)

        assertEquals("Review-annotated.pdf", second.name)
        assertEquals(File(app.cacheDir, DocumentService.SHARED_DIRECTORY), second.parentFile!!.parentFile)
        assertFalse("A new share replaces the previous copy", first.exists())
        PDDocument.load(second).use { document ->
            val contents = document.getPage(0).contents.readBytes().toString(Charsets.ISO_8859_1)
            assertTrue(contents.contains("\nS") || contents.contains(" S\n"))
        }
        val uri: Uri = FileProvider.getUriForFile(app, "${app.packageName}.files", second)
        assertEquals("content", uri.scheme)
    }
}
