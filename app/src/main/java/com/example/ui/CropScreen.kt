package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import com.example.util.ImageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    viewModel: MainViewModel,
    onNavigateToFilter: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val rawPath = viewModel.tempRawImagePath
    
    // Loaded Raw Bitmap
    var rawBitmap by remember(rawPath) {
        mutableStateOf<Bitmap?>(null)
    }

    // Effect to load bitmap safely on secondary thread
    LaunchedEffect(rawPath) {
        if (rawPath.isNotBlank()) {
            val file = File(rawPath)
            if (file.exists()) {
                val options = BitmapFactory.Options().apply {
                    // Prevent memory overflow on very high res files
                    inSampleSize = 1
                }
                rawBitmap = BitmapFactory.decodeFile(rawPath, options)
            }
        }
    }

    val cropPoints by viewModel.cropPoints.collectAsState()
    val isEdgeDetecting by viewModel.isEdgeDetecting.collectAsState()
    val selectedPaperSize by viewModel.selectedPaperSize.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adjust Boundaries", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                },
                actions = {
                    // Rotate image button
                    IconButton(onClick = {
                        val currentBmp = rawBitmap
                        if (currentBmp != null) {
                            val matrix = Matrix().apply { postRotate(90f) }
                            val rotated = Bitmap.createBitmap(
                                currentBmp, 0, 0, currentBmp.width, currentBmp.height, matrix, true
                            )
                            // Overwrite raw file
                            try {
                                val file = File(rawPath)
                                FileOutputStream(file).use { out ->
                                    rotated.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                                rawBitmap = rotated
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }) {
                        Icon(Icons.Filled.RotateRight, contentDescription = "Rotate Clockwise")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isEdgeDetecting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "AI detecting paper boundaries...",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Boundary Interactive Editor
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val containerWidth = maxWidth
                val containerHeight = maxHeight

                val density = LocalDensity.current
                val containerWidthPx = with(density) { containerWidth.toPx() }
                val containerHeightPx = with(density) { containerHeight.toPx() }

                val bmp = rawBitmap
                if (bmp != null) {
                    val bmpW = bmp.width.toFloat()
                    val bmpH = bmp.height.toFloat()

                    // Fit image calculations
                    val scaleX = containerWidthPx / bmpW
                    val scaleY = containerHeightPx / bmpH
                    val scale = Math.min(scaleX, scaleY)

                    val imgWPx = bmpW * scale
                    val imgHPx = bmpH * scale

                    val offsetX = (containerWidthPx - imgWPx) / 2
                    val offsetY = (containerHeightPx - imgHPx) / 2

                    // Draw image + overlay polygon lines
                    Box(
                        modifier = Modifier
                            .size(containerWidth, containerHeight)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // 1. Draw image bitmap using pure Compose drawImage
                            drawImage(
                                image = bmp.asImageBitmap(),
                                dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                                dstSize = IntSize(imgWPx.roundToInt(), imgHPx.roundToInt())
                            )

                            // Compute pixel points on the screen
                            val tl = Offset(offsetX + cropPoints[0].x * imgWPx, offsetY + cropPoints[0].y * imgHPx)
                            val tr = Offset(offsetX + cropPoints[1].x * imgWPx, offsetY + cropPoints[1].y * imgHPx)
                            val br = Offset(offsetX + cropPoints[2].x * imgWPx, offsetY + cropPoints[2].y * imgHPx)
                            val bl = Offset(offsetX + cropPoints[3].x * imgWPx, offsetY + cropPoints[3].y * imgHPx)

                            // 2. Draw Poly overlay
                            val path = Path().apply {
                                moveTo(tl.x, tl.y)
                                lineTo(tr.x, tr.y)
                                lineTo(br.x, br.y)
                                lineTo(bl.x, bl.y)
                                close()
                            }
                            drawPath(path, color = Color(0x3338BDF8)) // Area translucent fill
                            
                            val lineColor = Color(0xFF38BDF8)
                            val lineW = 3.dp.toPx()
                            val capRound = androidx.compose.ui.graphics.StrokeCap.Round

                            // Border lines
                            drawLine(lineColor, tl, tr, strokeWidth = lineW, cap = capRound)
                            drawLine(lineColor, tr, br, strokeWidth = lineW, cap = capRound)
                            drawLine(lineColor, br, bl, strokeWidth = lineW, cap = capRound)
                            drawLine(lineColor, bl, tl, strokeWidth = lineW, cap = capRound)
                        }

                        // Render 4 handles
                        val handleRadiusDp = 18.dp
                        val handleRadiusPx = with(density) { handleRadiusDp.toPx() }

                        cropPoints.forEachIndexed { index, point ->
                            val handleX = offsetX + point.x * imgWPx
                            val handleY = offsetY + point.y * imgHPx

                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (handleX - handleRadiusPx).roundToInt(),
                                            (handleY - handleRadiusPx).roundToInt()
                                        )
                                    }
                                    .size(handleRadiusDp * 2)
                                    .border(2.dp, Color.White, CircleShape)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .pointerInput(index) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            
                                            val currentPointPxX = offsetX + cropPoints[index].x * imgWPx
                                            val currentPointPxY = offsetY + cropPoints[index].y * imgHPx
                                            
                                            val newPxX = currentPointPxX + dragAmount.x
                                            val newPxY = currentPointPxY + dragAmount.y

                                            // Map back to relative coordinate percentages (0-1)
                                            val newNormX = ((newPxX - offsetX) / imgWPx).coerceIn(0f, 1f)
                                            val newNormY = ((newPxY - offsetY) / imgHPx).coerceIn(0f, 1f)

                                            // Update coordinates list in VM
                                            val updatedPoints = cropPoints.toMutableList().apply {
                                                this[index] = PointF(newNormX, newNormY)
                                            }
                                            viewModel.updateCropPoints(updatedPoints)
                                        }
                                    }
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Editor Action Buttons Toolbar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Paper Size Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PAPER SIZE ASPECT RATIO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageUtils.PaperSize.values().forEach { paper ->
                            val isSelected = selectedPaperSize == paper
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        1.dp, 
                                        if (isSelected) MaterialTheme.colorScheme.primary 
                                        else Color.Transparent, 
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.setPaperSize(paper) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (paper == ImageUtils.PaperSize.AUTO) "Freeform" else paper.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // AI Edge detection button
                    Button(
                        onClick = { viewModel.autoDetectEdges() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        enabled = !isEdgeDetecting
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                            Text("AI Auto-Detect")
                        }
                    }

                    // Reset boundaries
                    OutlinedButton(
                        onClick = {
                            viewModel.updateCropPoints(
                                listOf(
                                    PointF(0.05f, 0.05f),
                                    PointF(0.95f, 0.05f),
                                    PointF(0.95f, 0.95f),
                                    PointF(0.05f, 0.95f)
                                )
                            )
                        },
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset")
                    }
                }

                // Proceed with perspective warp
                Button(
                    onClick = onNavigateToFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Crop & Straighten", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}
