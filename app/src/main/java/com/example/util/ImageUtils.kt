package com.example.util

import android.graphics.*
import android.graphics.Matrix
import android.graphics.PointF

object ImageUtils {

    /**
     * Performs a 4-point perspective warp to crop and straighten a bitmap.
     * @param srcBitmap The source bitmap.
     * @param points Relative points [TL, TR, BR, BL] as proportions of width/height (0.0 to 1.0).
     */
    fun perspectiveCrop(srcBitmap: Bitmap, points: List<PointF>): Bitmap {
        if (points.size < 4) return srcBitmap

        val w = srcBitmap.width.toFloat()
        val h = srcBitmap.height.toFloat()

        // Map relative coordinates to absolute pixel coordinates
        val tlX = points[0].x * w
        val tlY = points[0].y * h
        val trX = points[1].x * w
        val trY = points[1].y * h
        val brX = points[2].x * w
        val brY = points[2].y * h
        val blX = points[3].x * w
        val blY = points[3].y * h

        // Compute width of the new image (max of top and bottom widths)
        val widthBottom = Math.hypot((brX - blX).toDouble(), (brY - blY).toDouble())
        val widthTop = Math.hypot((trX - tlX).toDouble(), (trY - tlY).toDouble())
        val destWidth = Math.max(widthBottom, widthTop).toInt().coerceAtLeast(100)

        // Compute height of the new image (max of right and left heights)
        val heightRight = Math.hypot((trX - brX).toDouble(), (trY - brY).toDouble())
        val heightLeft = Math.hypot((tlX - blX).toDouble(), (tlY - blY).toDouble())
        val destHeight = Math.max(heightRight, heightLeft).toInt().coerceAtLeast(100)

        // Create the destination bitmap
        val destBitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(destBitmap)

        val srcPoints = floatArrayOf(
            tlX, tlY,
            trX, trY,
            brX, brY,
            blX, blY
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            destWidth.toFloat(), 0f,
            destWidth.toFloat(), destHeight.toFloat(),
            0f, destHeight.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(srcBitmap, matrix, paint)

        return destBitmap
    }

    /**
     * Applies a document filter to a bitmap.
     */
    fun applyFilter(srcBitmap: Bitmap, filterType: String): Bitmap {
        return when (filterType.uppercase()) {
            "ENHANCED" -> applyMagicColor(srcBitmap)
            "BW" -> applyBlackAndWhite(srcBitmap)
            "GRAYSCALE" -> applyGrayscale(srcBitmap)
            "SHARP" -> applySharpContrast(srcBitmap)
            else -> srcBitmap // ORIGINAL
        }
    }

    private fun applyMagicColor(srcBitmap: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, srcBitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Increase contrast and brightness
        val contrast = 1.35f
        val brightness = 10f
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(srcBitmap, 0f, 0f, paint)
        return dest
    }

    private fun applyBlackAndWhite(srcBitmap: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, srcBitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Convert to grayscale
        val grayMatrix = ColorMatrix()
        grayMatrix.setSaturation(0f)
        
        // Apply extreme contrast to simulate photocopy/binary black & white
        val contrast = 2.4f
        val brightness = -75f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        
        // Combine them
        contrastMatrix.postConcat(grayMatrix)
        
        paint.colorFilter = ColorMatrixColorFilter(contrastMatrix)
        canvas.drawBitmap(srcBitmap, 0f, 0f, paint)
        return dest
    }

    private fun applyGrayscale(srcBitmap: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, srcBitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(srcBitmap, 0f, 0f, paint)
        return dest
    }

    private fun applySharpContrast(srcBitmap: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, srcBitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Mild contrast boost and saturation boost to make details crisp
        val contrast = 1.15f
        val brightness = -5f
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(srcBitmap, 0f, 0f, paint)
        return dest
    }

    enum class PaperSize(val displayName: String, val aspectRatio: Float?) {
        AUTO("Auto", null),
        A4("A4", 1.4142f),
        LETTER("Letter", 1.2941f),
        LEGAL("Legal", 1.6471f),
        BUSINESS_CARD("Card", 1.75f),
        RECEIPT("Receipt", 3.0f)
    }

    fun snapToPaperSize(
        points: List<PointF>,
        paperSize: PaperSize,
        imgWidth: Float,
        imgHeight: Float
    ): List<PointF> {
        val ratio = paperSize.aspectRatio ?: return points
        if (points.size < 4) return points

        // Calculate current center of the points bounding box
        val xs = points.map { it.x }
        val ys = points.map { it.y }
        val minX = xs.minOrNull() ?: 0.1f
        val maxX = xs.maxOrNull() ?: 0.9f
        val minY = ys.minOrNull() ?: 0.1f
        val maxY = ys.maxOrNull() ?: 0.9f

        val currentW = maxX - minX
        val currentH = maxY - minY
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        // Let's determine if image is portrait or landscape
        val isPortrait = imgHeight >= imgWidth
        val targetRatio = if (isPortrait) ratio else 1f / ratio

        // Aspect ratio of the image
        val imgRatio = imgHeight / imgWidth

        // We want physical aspect ratio pH / pW = targetRatio
        // In normalized coords: (hNorm * imgHeight) / (wNorm * imgWidth) = targetRatio
        // => hNorm / wNorm = targetRatio * (imgWidth / imgHeight) = targetRatio / imgRatio
        val targetRatioNorm = targetRatio / imgRatio

        // Try to match the width first and calculate height
        var wNorm = currentW.coerceAtLeast(0.1f)
        var hNorm = wNorm * targetRatioNorm

        // If height goes out of bounds, scale down both
        if (hNorm > 0.98f) {
            hNorm = 0.95f
            wNorm = hNorm / targetRatioNorm
        }
        // If width goes out of bounds, scale down both
        if (wNorm > 0.98f) {
            wNorm = 0.95f
            hNorm = wNorm * targetRatioNorm
        }

        val halfW = wNorm / 2f
        val halfH = hNorm / 2f

        val left = (centerX - halfW).coerceIn(0.01f, 0.99f)
        val right = (centerX + halfW).coerceIn(0.01f, 0.99f)
        val top = (centerY - halfH).coerceIn(0.01f, 0.99f)
        val bottom = (centerY + halfH).coerceIn(0.01f, 0.99f)

        return listOf(
            PointF(left, top),
            PointF(right, top),
            PointF(right, bottom),
            PointF(left, bottom)
        )
    }
}
