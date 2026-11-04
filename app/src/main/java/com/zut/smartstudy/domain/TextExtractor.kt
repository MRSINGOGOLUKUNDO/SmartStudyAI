package com.zut.smartstudy.domain

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads raw text from a document chosen through the Storage Access Framework.
 * Supports PDF (PdfBox-Android), DOCX (zipped XML) and plain text.
 */
object TextExtractor {

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return uri.lastPathSegment ?: "document"
    }

    /** Blocking – call from a background dispatcher. */
    fun extract(context: Context, uri: Uri): String {
        val name = displayName(context, uri).lowercase()
        val mime = context.contentResolver.getType(uri).orEmpty()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open the selected file.")

        val text = input.use { stream ->
            when {
                name.endsWith(".pdf") || mime == "application/pdf" -> extractPdf(stream)
                name.endsWith(".docx") || mime.contains("wordprocessingml") -> extractDocx(stream)
                name.endsWith(".txt") || mime.startsWith("text/") -> stream.bufferedReader().readText()
                else -> throw IllegalArgumentException(
                    "Unsupported file type. Please choose a PDF or a .docx Word file."
                )
            }
        }
        if (text.isBlank()) {
            throw IOException("No readable text found (scanned/image-only PDFs are not supported).")
        }
        return text
    }

    private fun extractPdf(stream: InputStream): String =
        PDDocument.load(stream).use { doc -> PDFTextStripper().getText(doc) }

    private fun extractDocx(stream: InputStream): String {
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    return xml
                        .replace("</w:p>", "\n\n")
                        .replace(Regex("<w:(tab|br)\\s*/>"), " ")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'")
                }
                entry = zip.nextEntry
            }
        }
        throw IOException("This .docx file appears to be corrupted.")
    }
}
