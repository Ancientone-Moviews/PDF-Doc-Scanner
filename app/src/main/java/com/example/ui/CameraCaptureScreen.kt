package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraCaptureScreen(
    viewModel: MainViewModel,
    onNavigateToCrop: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        // Permission Request UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Camera Permission Required",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "We need camera access to capture paper documents, receipts, business cards, and whiteboards directly using your smartphone camera.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Permission")
                    }
                    TextButton(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            }
        }
    } else {
        // Active Camera Preview Viewport UI
        CameraView(
            viewModel = viewModel,
            context = context,
            onNavigateToCrop = onNavigateToCrop,
            onNavigateBack = onNavigateBack
        )
    }
}

@Composable
fun CameraView(
    viewModel: MainViewModel,
    context: Context,
    onNavigateToCrop: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // CameraX parameters
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    var camera by remember { mutableStateOf<Camera?>(null) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }

    // Shutter states
    var isCapturing by remember { mutableStateOf(false) }

    val isBatchMode by viewModel.isBatchMode.collectAsState()
    val batchPages by viewModel.batchRawImagePaths.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }

    // Active Category Selection
    val categories = listOf("Document", "Receipt", "Business Card", "Whiteboard", "Note")
    var selectedCategory by remember { mutableStateOf("Document") }

    // Viewfinder laser animations
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_animation"
    )

    // Bind camera lifecycle
    LaunchedEffect(key1 = cameraSelector) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e("CameraView", "Binding camera provider failed", e)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ComposeColor.Black)) {
        // Raw Viewfinder Stream
        AndroidView(
            factory = {
                previewView.apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Guide Brackets + Laser scanning beam
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Crop frame margins
            val left = width * 0.12f
            val top = height * 0.20f
            val right = width * 0.88f
            val bottom = height * 0.70f

            // 1. Draw outer shaded background overlay using 4 Compose rectangles
            val overlayColor = ComposeColor(0xCC0B0F19)
            drawRect(
                color = overlayColor,
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(width, top)
            )
            drawRect(
                color = overlayColor,
                topLeft = Offset(0f, bottom),
                size = androidx.compose.ui.geometry.Size(width, height - bottom)
            )
            drawRect(
                color = overlayColor,
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(left, bottom - top)
            )
            drawRect(
                color = overlayColor,
                topLeft = Offset(right, top),
                size = androidx.compose.ui.geometry.Size(width - right, bottom - top)
            )

            // 2. Draw modern guide brackets using Compose drawLine
            val bracketLen = 30.dp.toPx()
            val strokeW = 4.dp.toPx()
            val bracketColor = ComposeColor(0xFF38BDF8) // Glow Blue

            // Top-Left Bracket
            drawLine(bracketColor, Offset(left, top), Offset(left + bracketLen, top), strokeWidth = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(bracketColor, Offset(left, top), Offset(left, top + bracketLen), strokeWidth = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)

            // Top-Right Bracket
            drawLine(bracketColor, Offset(right, top), Offset(right - bracketLen, top), strokeWidth = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(bracketColor, Offset(right, top), Offset(right, top + bracketLen), strokeWidth = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)

            // Bottom-Left Bracket
            drawLine(bracketColor, Offset(left, bottom), Offset(left + bracketLen, bottom), strokeWidth = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(bracketColor, Offset(left, bottom), Offset(left, bottom - bracketLen), strokeWidth = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)

            // Bottom-Right Bracket
            drawLine(bracketColor, Offset(right, bottom), Offset(right - bracketLen, bottom), strokeWidth = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(bracketColor, Offset(right, bottom), Offset(right, bottom - bracketLen), strokeWidth = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)

            // 3. Draw Laser Line Animation Sweep
            val currentLaserY = top + (bottom - top) * ((laserOffset - 0.15f) / 0.70f)
            
            // Neon laser line
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        ComposeColor.Transparent,
                        ComposeColor(0xFF0284C7),
                        ComposeColor(0xFF38BDF8),
                        ComposeColor(0xFF0284C7),
                        ComposeColor.Transparent
                    )
                ),
                start = Offset(left - 10f, currentLaserY),
                end = Offset(right + 10f, currentLaserY),
                strokeWidth = 3.dp.toPx()
            )
        }

        // Toolbar Panel Top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    viewModel.clearBatch()
                    onNavigateBack()
                },
                modifier = Modifier.background(ComposeColor(0x66000000), CircleShape)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Go Back", tint = ComposeColor.White)
            }

            // Mode Selector: Single vs Batch
            Row(
                modifier = Modifier
                    .background(ComposeColor(0x88000000), RoundedCornerShape(20.dp))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (!isBatchMode) MaterialTheme.colorScheme.primary else ComposeColor.Transparent)
                        .clickable { viewModel.setBatchModeEnabled(false) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Single",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!isBatchMode) MaterialTheme.colorScheme.onPrimary else ComposeColor.White
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isBatchMode) MaterialTheme.colorScheme.primary else ComposeColor.Transparent)
                        .clickable { viewModel.setBatchModeEnabled(true) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Batch",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isBatchMode) MaterialTheme.colorScheme.onPrimary else ComposeColor.White
                    )
                }
            }

            // Toggle Flash button
            IconButton(
                onClick = {
                    flashMode = if (flashMode == ImageCapture.FLASH_MODE_OFF) {
                        ImageCapture.FLASH_MODE_ON
                    } else {
                        ImageCapture.FLASH_MODE_OFF
                    }
                    imageCapture.flashMode = flashMode
                },
                modifier = Modifier.background(ComposeColor(0x66000000), CircleShape)
            ) {
                Icon(
                    imageVector = if (flashMode == ImageCapture.FLASH_MODE_ON) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = "Flash Toggle",
                    tint = if (flashMode == ImageCapture.FLASH_MODE_ON) ComposeColor(0xFFFBBF24) else ComposeColor.White
                )
            }
        }

        // Shutter Controls + Category Selector Dial Bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category Selector Dial
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 40.dp)
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) ComposeColor(0xFF38BDF8) else ComposeColor(0x44000000))
                            .clickable { selectedCategory = category }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = category.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) ComposeColor(0xFF0F172A) else ComposeColor.White
                        )
                    }
                }
            }

            // Shutter Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Action: Preview Thumbnail (only visible in batch mode when pages are captured)
                if (isBatchMode && batchPages.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ComposeColor(0x66000000))
                            .border(1.5.dp, ComposeColor(0xFF38BDF8), RoundedCornerShape(12.dp))
                            .clickable { showReviewDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = File(batchPages.last()),
                            contentDescription = "Batch latest page",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .size(20.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = batchPages.size.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(54.dp))
                }

                // Shutter Center Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, ComposeColor.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (isCapturing) ComposeColor.DarkGray else ComposeColor.White)
                        .clickable(enabled = !isCapturing) {
                            isCapturing = true
                            
                            val outputDir = context.cacheDir
                            val tempFile = File(outputDir, "temp_raw_${System.currentTimeMillis()}.jpg")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

                            imageCapture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        isCapturing = false
                                        if (isBatchMode) {
                                            viewModel.addToBatch(tempFile.absolutePath)
                                        } else {
                                            viewModel.setCapturedRawImage(tempFile.absolutePath, selectedCategory)
                                            onNavigateToCrop()
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isCapturing = false
                                        Log.e("CameraView", "Photo capture failed", exception)
                                    }
                                }
                            )
                        }
                )

                // Right Action: Save/Finish Batch (only visible in batch mode when pages are captured)
                if (isBatchMode && batchPages.isNotEmpty()) {
                    IconButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier
                            .size(54.dp)
                            .background(ComposeColor(0xFF10B981), CircleShape) // Green Save checkmark
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Save Batch",
                            tint = ComposeColor.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(54.dp))
                }
            }
        }

        // Dialog for saving batch document
        if (showSaveDialog) {
            var titleText by remember { mutableStateOf("Doc Batch ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())}") }
            var notesText by remember { mutableStateOf("") }
            var batchCategory by remember { mutableStateOf(selectedCategory) }
            var filterType by remember { mutableStateOf("ENHANCED") } // ORIGINAL, ENHANCED, BW, GRAYSCALE
            var isSavingBatch by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isSavingBatch) showSaveDialog = false },
                title = {
                    Text("Save Batch Document", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isSavingBatch) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "Processing and saving pages...",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Applying filters and generating multi-page single file",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = titleText,
                                onValueChange = { titleText = it },
                                label = { Text("Document Title") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Filter selector
                            Text(
                                "APPLY IMAGE FILTER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "ORIGINAL" to "Original",
                                    "ENHANCED" to "Enhanced",
                                    "BW" to "B&W",
                                    "GRAYSCALE" to "Grayscale"
                                ).forEach { (type, label) ->
                                    val isFilterSelected = filterType == type
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isFilterSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .border(1.dp, if (isFilterSelected) MaterialTheme.colorScheme.primary else ComposeColor.Transparent, RoundedCornerShape(12.dp))
                                            .clickable { filterType = type }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isFilterSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isFilterSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Category selection
                            Text(
                                "DOCUMENT CATEGORY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Document", "Receipt", "Business Card", "Note").forEach { cat ->
                                    val isCatSelected = batchCategory == cat
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isCatSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable { batchCategory = cat }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            cat,
                                            fontSize = 10.sp,
                                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCatSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = notesText,
                                onValueChange = { notesText = it },
                                label = { Text("Notes (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Text(
                                "Pages count: ${batchPages.size}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!isSavingBatch) {
                        Button(
                            onClick = {
                                isSavingBatch = true
                                viewModel.saveBatchDocuments(
                                    title = titleText,
                                    category = batchCategory,
                                    filterType = filterType,
                                    notes = notesText,
                                    onComplete = {
                                        isSavingBatch = false
                                        showSaveDialog = false
                                        onNavigateBack() // Go back to dashboard on complete
                                    }
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save Document", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    if (!isSavingBatch) {
                        TextButton(onClick = { showSaveDialog = false }) {
                            Text("Cancel")
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Dialog for reviewing and deleting captured batch pages
        if (showReviewDialog) {
            AlertDialog(
                onDismissRequest = { showReviewDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Review Batch Pages", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${batchPages.size} total", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                },
                text = {
                    if (batchPages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                            Text("No pages captured yet.", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(batchPages) { index, path ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Thumbnail preview
                                            Box(
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(ComposeColor.Black),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AsyncImage(
                                                    model = File(path),
                                                    contentDescription = "Batch Page thumbnail",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }

                                            Column {
                                                Text(
                                                    "Page ${index + 1}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    "Raw image file",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { viewModel.removeFromBatch(index) }
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Delete Page",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showReviewDialog = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Back to Camera")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}
