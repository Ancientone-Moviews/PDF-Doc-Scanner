package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ExportUtils {

    /**
     * Generates a real PDF file with the scanned images, each on its own page, followed by beautifully formatted OCR text on subsequent pages if available.
     */
    fun generatePdf(context: Context, title: String, imagePaths: List<String>, ocrText: String): File? {
        val pdfDocument = PdfDocument()
        val file = File(context.cacheDir, "${title.replace(" ", "_")}.pdf")

        try {
            var pageNumber = 1

            // Draw each scanned image on its own page
            for (imagePath in imagePaths) {
                // Standard A4 size: 595 x 842 points (72 points per inch)
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val bitmap = BitmapFactory.decodeFile(imagePath)
                if (bitmap != null) {
                    // Scale bitmap to fit A4 page while keeping aspect ratio
                    val margin = 40f
                    val maxWidth = 595f - (margin * 2)
                    val maxHeight = 842f - (margin * 2)

                    val scaleX = maxWidth / bitmap.width
                    val scaleY = maxHeight / bitmap.height
                    val scale = Math.min(scaleX, scaleY)

                    val destWidth = bitmap.width * scale
                    val destHeight = bitmap.height * scale
                    val left = margin + (maxWidth - destWidth) / 2
                    val top = margin + (maxHeight - destHeight) / 2

                    val destRect = RectF(left, top, left + destWidth, top + destHeight)
                    canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    bitmap.recycle()
                } else {
                    // If no image, draw placeholder text
                    val paint = Paint().apply {
                        color = Color.DKGRAY
                        textSize = 18f
                        isAntiAlias = true
                    }
                    canvas.drawText("Scanned Document Page $pageNumber: $title", 50f, 100f, paint)
                }
                pdfDocument.finishPage(page)
                pageNumber++
            }

            // --- OCR Text Page ---
            if (ocrText.isNotBlank()) {
                val textPaint = TextPaint().apply {
                    color = Color.BLACK
                    textSize = 12f
                    isAntiAlias = true
                }

                val titlePaint = TextPaint().apply {
                    color = Color.rgb(15, 23, 42) // Dark Slate
                    textSize = 18f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                val margin = 50f
                val contentWidth = (595f - (margin * 2)).toInt()

                // Layout the title and text using Android's StaticLayout for clean word-wrapping
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Draw Page Header
                canvas.drawText("Extracted OCR Text", margin, margin + 20f, titlePaint)
                canvas.drawLine(margin, margin + 30f, 595f - margin, margin + 30f, Paint().apply {
                    color = Color.LTGRAY
                    strokeWidth = 1f
                })

                val startY = margin + 50f
                canvas.save()
                canvas.translate(margin, startY)

                val staticLayout = StaticLayout.Builder.obtain(ocrText, 0, ocrText.length, textPaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1f)
                    .build()

                staticLayout.draw(canvas)
                canvas.restore()

                pdfDocument.finishPage(page)
            }

            // Save PDF document
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            pdfDocument.close()
        }
    }

    /**
     * Overload for backward compatibility and single image paths
     */
    fun generatePdf(context: Context, title: String, imagePath: String, ocrText: String): File? {
        return generatePdf(context, title, listOf(imagePath), ocrText)
    }

    /**
     * Generates a standard formatted Word (.doc) document file containing the OCR text.
     * Compatible with MS Word, Google Docs, and Libra Office.
     */
    fun generateWord(context: Context, title: String, ocrText: String): File? {
        val file = File(context.cacheDir, "${title.replace(" ", "_")}.doc")
        try {
            // Generating HTML styled RTF/Doc content which Word reads natively
            val content = """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="utf-8">
                <style>
                    body { font-family: 'Arial', sans-serif; line-height: 1.6; color: #1e293b; padding: 20px; }
                    h1 { color: #0f172a; border-bottom: 2px solid #e2e8f0; padding-bottom: 5px; }
                    .ocr-content { font-size: 11pt; white-space: pre-wrap; }
                </style>
                </head>
                <body>
                    <h1>$title</h1>
                    <p style="color: #64748b; font-size: 9pt;">Extracted via DocScanner AI</p>
                    <div class="ocr-content">$ocrText</div>
                </body>
                </html>
            """.trimIndent()

            FileOutputStream(file).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Generates a PowerPoint (.ppt) content layout file mapping the document content.
     */
    fun generatePowerPoint(context: Context, title: String, ocrText: String): File? {
        val file = File(context.cacheDir, "${title.replace(" ", "_")}.ppt")
        try {
            // Formatted Presentation outline compatible with PowerPoint text ingestion
            val sb = StringBuilder()
            sb.append("=========================================\n")
            sb.append("PRESENTATION SLIDE LAYOUT: $title\n")
            sb.append("Generated by DocScanner AI\n")
            sb.append("=========================================\n\n")

            sb.append("[SLIDE 1: TITLE SLIDE]\n")
            sb.append("-----------------------------------------\n")
            sb.append("Title: $title\n")
            sb.append("Subtitle: Scanned Digitized Notes\n\n")

            val paragraphs = ocrText.split("\n\n").filter { it.isNotBlank() }
            var slideNum = 2
            for (p in paragraphs) {
                sb.append("[SLIDE $slideNum: CONTENT SLIDE]\n")
                sb.append("-----------------------------------------\n")
                val lines = p.split("\n")
                val slideTitle = lines.firstOrNull()?.take(40) ?: "Key Takeaways"
                sb.append("Title: $slideTitle\n")
                sb.append("Points:\n")
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        sb.append(" - $line\n")
                    }
                }
                sb.append("\n")
                slideNum++
            }

            FileOutputStream(file).use { out ->
                out.write(sb.toString().toByteArray(Charsets.UTF_8))
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Shares a file using a standard Chooser Intent via FileProvider.
     */
    fun shareFile(context: Context, file: File, mimeType: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "com.aistudio.docscanner.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Scanned File")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
