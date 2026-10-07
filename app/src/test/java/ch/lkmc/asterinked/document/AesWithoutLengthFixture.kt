package ch.lkmc.asterinked.document

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.encryption.PDEncryption
import java.io.File

internal enum class AesWithoutLengthFixture(val keyBits: Int, val version: Int, val revision: Int, val method: COSName) {
    AESV2(128, PDEncryption.VERSION4_SECURITY_HANDLER, 4, COSName.AESV2),
    AESV3(256, 5, 6, COSName.AESV3);

    fun omitTopLevelLength(source: File) {
        var pdf = source.readText(Charsets.ISO_8859_1)
        // Edit only PDFBox's generated dictionary. Whitespace preserves xref offsets
        // and ciphertext; saving through PDFBox would restore the top-level /Length.
        val header = Regex("""/Filter /Standard\s+/V $version\s+/R $revision\s+(/Length $keyBits)\b""")
        val length = requireNotNull(header.find(pdf)?.groups?.get(1)) { "Missing encryption /Length in $source" }
        pdf = pdf.replaceRange(length.range, " ".repeat(length.value.length))

        // ISO 32000's standard crypt filter /Length is in bytes; PDFBox writes bits.
        val filter = Regex("""/CFM /${method.name}\s+/Length ($keyBits)\b""")
        val filterLength = requireNotNull(filter.find(pdf)?.groups?.get(1)) { "Missing AES crypt filter in $source" }
        val bytes = (keyBits / Byte.SIZE_BITS).toString().padEnd(filterLength.value.length)
        source.writeText(pdf.replaceRange(filterLength.range, bytes), Charsets.ISO_8859_1)
    }
}
