package ch.lkmc.asterinked.document

import android.net.Uri
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfEngineSecurityTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var engine: PdfEngine

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(app)
        engine = PdfEngine(app.cacheDir)
    }

    @Test fun ownerRestrictedPdfOpensAndTheCopyKeepsItsRestrictions() {
        for ((keyLength, aes) in listOf(40 to false, 128 to false, 128 to true, 256 to true)) {
            val source = protectedPdf(user = "", keyLength = keyLength, aes = aes) { setCanPrint(false) }
            val sourceRevision = PDDocument.load(source).use { it.encryption.revision }
            assertEquals("bits=$keyLength", 1, engine.inspect(source).size)

            val exported = file("exported-$keyLength")
            engine.export(source, exported, mapOf(0 to listOf(InkStroke(listOf(InkPoint(10f, 10f, 1f), InkPoint(60f, 40f, 1f)), 0, 2f))))

            PDDocument.load(exported).use { document ->
                assertTrue("bits=$keyLength stays encrypted", document.isEncrypted)
                assertTrue("bits=$keyLength aes=$aes is not downgraded", document.encryption.revision >= sourceRevision)
                assertFalse("bits=$keyLength keeps no-print", document.currentAccessPermission.canPrint())
                assertTrue("bits=$keyLength still allows changes", document.currentAccessPermission.canModify())
                assertTrue(PDFTextStripper().getText(document).contains(SENTINEL))
                val contents = document.getPage(0).contents.readBytes().toString(Charsets.ISO_8859_1)
                assertTrue("bits=$keyLength has vector ink", contents.contains("\nS") || contents.contains(" S\n"))
            }
            // Repeated exports from the protected source keep working.
            engine.export(source, file("again-$keyLength"), emptyMap())
        }
    }

    @Test fun plainPdfExportsStayUnencrypted() {
        val source = file("plain")
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.save(source)
        }
        val exported = file("plain-exported")
        engine.export(source, exported, mapOf(0 to listOf(InkStroke(listOf(InkPoint(10f, 10f, 1f)), 0, 2f))))
        PDDocument.load(exported).use { assertFalse(it.isEncrypted) }
    }

    @Test fun pdfThatNeedsAPasswordIsReportedAsSuch() {
        assertProblem(DocumentProblem.PASSWORD_PROTECTED) { engine.inspect(protectedPdf(user = "secret")) }
    }

    @Test fun pdfThatForbidsChangesIsReportedAsSuch() {
        assertProblem(DocumentProblem.EDITING_NOT_ALLOWED) { engine.inspect(protectedPdf(user = "") { setCanModify(false) }) }
    }

    @Test fun openingThroughTheServiceReportsTheProblemAndKeepsNoCopy() {
        val service = DocumentService(app)
        val documents = File(app.filesDir, "documents")
        val junk = file("junk").apply { writeText("not a pdf at all") }

        assertProblem(DocumentProblem.NOT_A_PDF) { service.open(Uri.fromFile(junk)) }
        assertProblem(DocumentProblem.PASSWORD_PROTECTED) { service.open(Uri.fromFile(protectedPdf(user = "secret"))) }
        assertProblem(DocumentProblem.SOURCE_UNREADABLE) { service.open(Uri.fromFile(File(app.cacheDir, "missing.pdf"))) }

        assertTrue("Failed imports leave no private copy", documents.listFiles().orEmpty().none { it.extension == "pdf" })
    }

    @Test fun aPdfNestedTooDeepIsReportedInsteadOfCrashing() {
        val service = DocumentService(app)
        val documents = File(app.filesDir, "documents")

        // PDFBox parses nested arrays recursively; this depth overflows its stack.
        assertProblem(DocumentProblem.NOT_A_PDF) { service.open(Uri.fromFile(deeplyNestedPdf(depth = 100_000))) }

        assertTrue("A failed import leaves no private copy", documents.listFiles().orEmpty().none { it.extension == "pdf" })
    }

    private fun assertProblem(expected: DocumentProblem, work: () -> Unit) {
        try {
            work()
            fail("Expected $expected")
        } catch (error: DocumentException) {
            assertEquals(expected, error.problem)
        }
    }

    private fun protectedPdf(user: String, keyLength: Int = 128, aes: Boolean = false, permissions: AccessPermission.() -> Unit = {}): File {
        val output = file("protected")
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(72f, 700f)
                stream.showText(SENTINEL)
                stream.endText()
            }
            val policy = StandardProtectionPolicy("owner", user, AccessPermission().apply(permissions))
            policy.encryptionKeyLength = keyLength
            policy.isPreferAES = aes
            document.protect(policy)
            document.save(output)
        }
        return output
    }

    // A one-page PDF whose page dictionary carries an array nested [depth] levels deep.
    private fun deeplyNestedPdf(depth: Int): File {
        val bytes = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        fun obj(body: String) {
            offsets += bytes.length
            bytes.append("${offsets.size} 0 obj $body endobj\n")
        }
        obj("<< /Type /Catalog /Pages 2 0 R >>")
        obj("<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
        obj("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Deep ${"[".repeat(depth)}${"]".repeat(depth)} >>")
        val xref = bytes.length
        bytes.append("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { bytes.append(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
        bytes.append("trailer << /Root 1 0 R /Size ${offsets.size + 1} >>\nstartxref\n$xref\n%%EOF\n")
        return file("deep").apply { writeText(bytes.toString(), Charsets.ISO_8859_1) }
    }

    private fun file(name: String) = File(app.cacheDir, "$name-${System.nanoTime()}.pdf")

    private companion object {
        const val SENTINEL = "Secured sentinel"
    }
}
