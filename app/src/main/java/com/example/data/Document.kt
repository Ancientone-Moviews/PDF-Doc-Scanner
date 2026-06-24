package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String, // Receipt, Business Card, Whiteboard, Note, Document
    val ocrText: String = "",
    val imagePath: String, // Cropped / enhanced JPEG
    val originalImagePath: String, // Raw captured photo
    val cropPoints: String = "", // "0.1,0.1;0.9,0.1;0.9,0.9;0.1,0.9" (x,y of TL, TR, BR, BL)
    val filterType: String = "ENHANCED", // ORIGINAL, ENHANCED, BW, GRAYSCALE
    val isSynced: Boolean = false,
    val cloudId: String? = null,
    val notes: String = "",
    val additionalImagePaths: String = "", // Pipe-separated list: "path2|path3|..."
    val additionalOriginalImagePaths: String = "" // Pipe-separated list: "rawPath2|rawPath3|..."
) {
    fun getProcessedImagePaths(): List<String> {
        return if (additionalImagePaths.isBlank()) {
            listOf(imagePath)
        } else {
            listOf(imagePath) + additionalImagePaths.split("|").filter { it.isNotBlank() }
        }
    }

    fun getRawImagePaths(): List<String> {
        return if (additionalOriginalImagePaths.isBlank()) {
            listOf(originalImagePath)
        } else {
            listOf(originalImagePath) + additionalOriginalImagePaths.split("|").filter { it.isNotBlank() }
        }
    }
}
