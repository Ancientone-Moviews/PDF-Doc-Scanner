package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Document
import com.example.data.DocumentRepository
import com.example.data.GeminiClient
import com.example.util.ExportUtils
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MainViewModel"
    private val context = application.applicationContext
    private val repository: DocumentRepository

    // Database Flows
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val documents: StateFlow<List<Document>> = combine(
        _searchQuery,
        _selectedCategory
    ) { query, category ->
        Pair(query, category)
    }.flatMapLatest { (query, category) ->
        if (query.isBlank()) {
            repository.allDocuments.map { list ->
                if (category == "All") list else list.filter { it.category == category }
            }
        } else {
            repository.searchDocuments(query).map { list ->
                if (category == "All") list else list.filter { it.category == category }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Scanning/Capture State Flow
    var tempRawImagePath: String = ""
    var currentCaptureCategory: String = "Document"

    val isBatchMode = MutableStateFlow(false)
    val batchRawImagePaths = MutableStateFlow<List<String>>(emptyList())

    val selectedPaperSize = MutableStateFlow(ImageUtils.PaperSize.AUTO)

    private val _cropPoints = MutableStateFlow<List<PointF>>(
        listOf(PointF(0.1f, 0.1f), PointF(0.9f, 0.1f), PointF(0.9f, 0.9f), PointF(0.1f, 0.9f))
    )
    val cropPoints: StateFlow<List<PointF>> = _cropPoints.asStateFlow()

    private val _isEdgeDetecting = MutableStateFlow(false)
    val isEdgeDetecting: StateFlow<Boolean> = _isEdgeDetecting.asStateFlow()

    // Active document being viewed
    private val _activeDocument = MutableStateFlow<Document?>(null)
    val activeDocument: StateFlow<Document?> = _activeDocument.asStateFlow()

    // AI Transcribing State (OCR)
    private val _ocrProcessingIds = MutableStateFlow<Set<Int>>(emptySet())
    val ocrProcessingIds: StateFlow<Set<Int>> = _ocrProcessingIds.asStateFlow()

    // Cloud Sync Simulation State
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<String>>(listOf("Cloud storage initialized securely."))
    val syncLogs: StateFlow<List<String>> = _syncLogs.asStateFlow()

    private val _cloudStorageUsedBytes = MutableStateFlow(0L)
    val cloudStorageUsedBytes: StateFlow<Long> = _cloudStorageUsedBytes.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(context)
        repository = DocumentRepository(database.documentDao())
        
        // Compute virtual cloud usage from database entries marked synced
        viewModelScope.launch {
            repository.allDocuments.collect { list ->
                val syncedSize = list.filter { it.isSynced }.sumOf { 
                    val file = File(it.imagePath)
                    if (file.exists()) file.length() else 0L
                }
                _cloudStorageUsedBytes.value = syncedSize
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateCropPoints(points: List<PointF>) {
        _cropPoints.value = points
    }

    fun setCapturedRawImage(path: String, category: String) {
        tempRawImagePath = path
        currentCaptureCategory = category
        selectedPaperSize.value = ImageUtils.PaperSize.AUTO
        // Reset crop points to default safe margins
        _cropPoints.value = listOf(
            PointF(0.15f, 0.15f), // TL
            PointF(0.85f, 0.15f), // TR
            PointF(0.85f, 0.85f), // BR
            PointF(0.15f, 0.85f)  // BL
        )
    }

    fun setPaperSize(paperSize: ImageUtils.PaperSize) {
        selectedPaperSize.value = paperSize
        if (paperSize != ImageUtils.PaperSize.AUTO && tempRawImagePath.isNotBlank()) {
            val file = File(tempRawImagePath)
            if (file.exists()) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tempRawImagePath, options)
                val imgW = options.outWidth.toFloat()
                val imgH = options.outHeight.toFloat()
                if (imgW > 0 && imgH > 0) {
                    val snapped = ImageUtils.snapToPaperSize(_cropPoints.value, paperSize, imgW, imgH)
                    _cropPoints.value = snapped
                }
            }
        }
    }

    /**
     * Uses Gemini to detect document edges automatically
     */
    fun autoDetectEdges() {
        if (tempRawImagePath.isBlank()) return
        viewModelScope.launch {
            _isEdgeDetecting.value = true
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(tempRawImagePath)
                }
                if (bitmap != null) {
                    val paperSizeName = selectedPaperSize.value.displayName
                    val detected = GeminiClient.detectEdges(bitmap, paperSizeName)
                    if (detected != null && detected.size == 4) {
                        val finalPoints = if (selectedPaperSize.value != ImageUtils.PaperSize.AUTO) {
                            ImageUtils.snapToPaperSize(detected, selectedPaperSize.value, bitmap.width.toFloat(), bitmap.height.toFloat())
                        } else {
                            detected
                        }
                        _cropPoints.value = finalPoints
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Edge auto-detection failed", e)
            } finally {
                _isEdgeDetecting.value = false
            }
        }
    }

    /**
     * Save raw bitmap to a temporary cache file
     */
    fun saveTempRawBitmap(bitmap: Bitmap): String? {
        return try {
            val file = File(context.cacheDir, "temp_raw_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving raw bitmap", e)
            null
        }
    }

    /**
     * Performs perspective crop, applies selected filter, and saves permanently.
     */
    fun processAndSaveDocument(
        title: String,
        filterType: String,
        notes: String,
        onComplete: () -> Unit
    ) {
        if (tempRawImagePath.isBlank()) return

        viewModelScope.launch {
            try {
                // 1. Perspective Crop (IO thread)
                val croppedBitmap = withContext(Dispatchers.IO) {
                    val rawBitmap = BitmapFactory.decodeFile(tempRawImagePath)
                    if (rawBitmap != null) {
                        val cropped = ImageUtils.perspectiveCrop(rawBitmap, _cropPoints.value)
                        rawBitmap.recycle()
                        cropped
                    } else null
                }

                if (croppedBitmap == null) {
                    Log.e(TAG, "Failed to decode original raw photo")
                    return@launch
                }

                // 2. Apply chosen Image filter
                val filteredBitmap = withContext(Dispatchers.IO) {
                    ImageUtils.applyFilter(croppedBitmap, filterType)
                }

                // 3. Save processed image permanently to files directory
                val finalImageFile = withContext(Dispatchers.IO) {
                    val fileName = "scan_${System.currentTimeMillis()}.jpg"
                    val file = File(context.filesDir, fileName)
                    FileOutputStream(file).use { out ->
                        filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    file
                }

                // 4. Copy raw photo permanently in case they want to re-crop/re-edit
                val permanentRawFile = withContext(Dispatchers.IO) {
                    val rawFile = File(tempRawImagePath)
                    if (rawFile.exists()) {
                        val destFile = File(context.filesDir, "raw_${System.currentTimeMillis()}.jpg")
                        rawFile.copyTo(destFile, overwrite = true)
                        destFile
                    } else {
                        null
                    }
                }

                // 5. Build cropPoints string
                val pointsStr = _cropPoints.value.joinToString(";") { "${it.x},${it.y}" }

                // 6. Insert document record into Room database
                val document = Document(
                    title = title.ifBlank { "Scan ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}" },
                    category = currentCaptureCategory,
                    imagePath = finalImageFile.absolutePath,
                    originalImagePath = permanentRawFile?.absolutePath ?: tempRawImagePath,
                    cropPoints = pointsStr,
                    filterType = filterType,
                    ocrText = "", // Extracted asynchronously
                    isSynced = false,
                    notes = notes
                )

                val insertId = repository.insert(document).toInt()

                // 7. Fire asynchronous Gemini OCR in background thread
                triggerBackgroundOcr(insertId, finalImageFile.absolutePath, currentCaptureCategory)

                // Clean up temporary files
                try {
                    val tempRaw = File(tempRawImagePath)
                    if (tempRaw.exists()) tempRaw.delete()
                } catch (e: Exception) {
                    // Ignore cleanup error
                }

                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing and saving scan", e)
            }
        }
    }

    /**
     * Triggers the Gemini OCR extraction on a background thread.
     */
    private fun triggerBackgroundOcr(docId: Int, imagePath: String, category: String) {
        _ocrProcessingIds.update { it + docId }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                if (bitmap != null) {
                    val ocrResult = GeminiClient.performOcr(bitmap, category)
                    
                    // Fetch existing doc and update OCR text
                    val currentDoc = repository.getDocumentById(docId)
                    if (currentDoc != null) {
                        val updatedDoc = currentDoc.copy(ocrText = ocrResult)
                        repository.update(updatedDoc)
                        
                        // If active document is the one we completed, update active document flow
                        if (_activeDocument.value?.id == docId) {
                            _activeDocument.value = updatedDoc
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Asynchronous OCR task failed", e)
            } finally {
                _ocrProcessingIds.update { it - docId }
            }
        }
    }

    fun setActiveDocument(document: Document?) {
        _activeDocument.value = document
    }

    fun updateActiveDocumentOcrText(newText: String) {
        val current = _activeDocument.value ?: return
        viewModelScope.launch {
            val updated = current.copy(ocrText = newText)
            repository.update(updated)
            _activeDocument.value = updated
        }
    }

    fun updateActiveDocumentDetails(title: String, category: String, notes: String) {
        val current = _activeDocument.value ?: return
        viewModelScope.launch {
            val updated = current.copy(title = title, category = category, notes = notes)
            repository.update(updated)
            _activeDocument.value = updated
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            // Delete files associated
            try {
                File(document.imagePath).delete()
                File(document.originalImagePath).delete()
                
                // Delete additional pages
                document.getProcessedImagePaths().drop(1).forEach { path ->
                    try { File(path).delete() } catch (e: Exception) {}
                }
                document.getRawImagePaths().drop(1).forEach { path ->
                    try { File(path).delete() } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting local scan image files", e)
            }
            repository.delete(document)
            if (_activeDocument.value?.id == document.id) {
                _activeDocument.value = null
            }
        }
    }

    // --- Batch Scanning Support Functions ---

    fun toggleBatchMode() {
        isBatchMode.update { !it }
        clearBatch()
    }

    fun setBatchModeEnabled(enabled: Boolean) {
        isBatchMode.value = enabled
        if (!enabled) clearBatch()
    }

    fun addToBatch(path: String) {
        batchRawImagePaths.update { it + path }
    }

    fun clearBatch() {
        // Delete raw cached files
        batchRawImagePaths.value.forEach { path ->
            try { File(path).delete() } catch (e: Exception) {}
        }
        batchRawImagePaths.value = emptyList()
    }

    fun removeFromBatch(index: Int) {
        batchRawImagePaths.update { current ->
            val newList = current.toMutableList()
            if (index in newList.indices) {
                try {
                    File(newList[index]).delete()
                } catch (e: Exception) {
                    // Ignore
                }
                newList.removeAt(index)
            }
            newList
        }
    }

    fun saveBatchDocuments(
        title: String,
        category: String,
        filterType: String,
        notes: String,
        onComplete: () -> Unit
    ) {
        val rawPaths = batchRawImagePaths.value
        if (rawPaths.isEmpty()) return

        viewModelScope.launch {
            try {
                val processedImagePaths = mutableListOf<String>()
                val permanentRawPaths = mutableListOf<String>()
                val ocrTexts = mutableListOf<String>()

                // Process each page in the batch
                for (i in rawPaths.indices) {
                    val rawPath = rawPaths[i]
                    
                    // 1. Load raw bitmap
                    val rawBitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(rawPath)
                    }
                    if (rawBitmap == null) continue

                    // 2. Perform default crop (Fast and reliable for batch)
                    val croppedBitmap = withContext(Dispatchers.IO) {
                        val defaultPoints = listOf(
                            PointF(0f, 0f),
                            PointF(1f, 0f),
                            PointF(1f, 1f),
                            PointF(0f, 1f)
                        )
                        ImageUtils.perspectiveCrop(rawBitmap, defaultPoints)
                    }

                    // 3. Apply filter
                    val filteredBitmap = withContext(Dispatchers.IO) {
                        ImageUtils.applyFilter(croppedBitmap, filterType)
                    }

                    // 4. Save processed image permanently
                    val finalImageFile = withContext(Dispatchers.IO) {
                        val fileName = "scan_batch_${System.currentTimeMillis()}_$i.jpg"
                        val file = File(context.filesDir, fileName)
                        FileOutputStream(file).use { out ->
                            filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        file
                    }
                    processedImagePaths.add(finalImageFile.absolutePath)

                    // 5. Copy raw photo permanently
                    val permanentRawFile = withContext(Dispatchers.IO) {
                        val rawFile = File(rawPath)
                        if (rawFile.exists()) {
                            val destFile = File(context.filesDir, "raw_batch_${System.currentTimeMillis()}_$i.jpg")
                            rawFile.copyTo(destFile, overwrite = true)
                            destFile
                        } else null
                    }
                    if (permanentRawFile != null) {
                        permanentRawPaths.add(permanentRawFile.absolutePath)
                    }

                    // 6. Asynchronous OCR via Gemini
                    try {
                        val ocrResult = GeminiClient.performOcr(filteredBitmap, category)
                        if (ocrResult.isNotBlank()) {
                            ocrTexts.add(ocrResult)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "OCR failed for batch item $i", e)
                    }

                    // Recycle bitmaps
                    rawBitmap.recycle()
                    croppedBitmap.recycle()
                    filteredBitmap.recycle()
                }

                if (processedImagePaths.isEmpty()) {
                    Log.e(TAG, "No pages processed in batch")
                    return@launch
                }

                // 7. Combine fields for the Room record
                val firstProcessedPath = processedImagePaths.first()
                val firstRawPath = permanentRawPaths.firstOrNull() ?: rawPaths.first()
                
                val additionalProcessedStr = if (processedImagePaths.size > 1) {
                    processedImagePaths.drop(1).joinToString("|")
                } else ""

                val additionalRawStr = if (permanentRawPaths.size > 1) {
                    permanentRawPaths.drop(1).joinToString("|")
                } else ""

                val combinedOcrText = ocrTexts.joinToString("\n\n---\n\n")

                val document = Document(
                    title = title.ifBlank { "Batch Scan ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}" },
                    category = category,
                    imagePath = firstProcessedPath,
                    originalImagePath = firstRawPath,
                    cropPoints = "0,0;1,0;1,1;0,1",
                    filterType = filterType,
                    ocrText = combinedOcrText,
                    isSynced = false,
                    notes = notes,
                    additionalImagePaths = additionalProcessedStr,
                    additionalOriginalImagePaths = additionalRawStr
                )

                repository.insert(document)

                // Clean up batch raw files in cache
                for (rawPath in rawPaths) {
                    try { File(rawPath).delete() } catch (e: Exception) {}
                }
                batchRawImagePaths.value = emptyList() // clear list but don't delete files again

                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving batch documents", e)
            }
        }
    }

    // --- Cloud Sync Simulator ---

    fun performCloudSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncLogs.value = listOf("Initializing cloud synchronization...", "Establishing secure SSL connection to virtual cloud hub...")
            
            // Fetch all unsynced documents
            val allDocsList = repository.allDocuments.first()
            val unsynced = allDocsList.filter { !it.isSynced }

            if (unsynced.isEmpty()) {
                _syncLogs.update { it + "No unsynced files. Device database is up to date!" }
                kotlinx.coroutines.delay(1000)
                _isSyncing.value = false
                return@launch
            }

            _syncLogs.update { it + "Found ${unsynced.size} pending scan(s) to synchronize." }
            kotlinx.coroutines.delay(1200)

            for (doc in unsynced) {
                _syncLogs.update { it + "Synchronizing: ${doc.title}..." }
                kotlinx.coroutines.delay(1000)

                // Update doc to synced state and assign cloudId
                val syncedDoc = doc.copy(isSynced = true, cloudId = "cloud_${UUID.randomUUID().toString().take(8)}")
                repository.update(syncedDoc)
                
                if (_activeDocument.value?.id == doc.id) {
                    _activeDocument.value = syncedDoc
                }

                _syncLogs.update { it + "✓ Verified backup of [${doc.category}] ${doc.title}." }
            }

            kotlinx.coroutines.delay(800)
            _syncLogs.update { it + "Analyzing account storage..." }
            kotlinx.coroutines.delay(1000)
            
            val totalSizeKb = unsynced.sumOf { File(it.imagePath).length() } / 1024f
            _syncLogs.update { it + "Cloud sync completed successfully. Uploaded ${String.format("%.1f", totalSizeKb)} KB." }
            _syncLogs.update { it + "Device is 100% in sync with Cloud." }
            
            _isSyncing.value = false
        }
    }

    // --- Export & Share Triggering ---

    fun exportAsPdf(document: Document) {
        viewModelScope.launch(Dispatchers.IO) {
            val allPages = document.getProcessedImagePaths()
            val file = ExportUtils.generatePdf(context, document.title, allPages, document.ocrText)
            if (file != null && file.exists()) {
                withContext(Dispatchers.Main) {
                    ExportUtils.shareFile(context, file, "application/pdf")
                }
            }
        }
    }

    fun exportAsWord(document: Document) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = ExportUtils.generateWord(context, document.title, document.ocrText)
            if (file != null && file.exists()) {
                withContext(Dispatchers.Main) {
                    ExportUtils.shareFile(context, file, "application/msword")
                }
            }
        }
    }

    fun exportAsPowerPoint(document: Document) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = ExportUtils.generatePowerPoint(context, document.title, document.ocrText)
            if (file != null && file.exists()) {
                withContext(Dispatchers.Main) {
                    ExportUtils.shareFile(context, file, "application/vnd.ms-powerpoint")
                }
            }
        }
    }

    fun shareImage(document: Document) {
        val file = File(document.imagePath)
        if (file.exists()) {
            ExportUtils.shareFile(context, file, "image/jpeg")
        }
    }
}
