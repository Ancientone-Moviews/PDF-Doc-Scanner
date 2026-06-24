package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(
    viewModel: MainViewModel,
    onSaveSuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val rawPath = viewModel.tempRawImagePath
    val cropPoints by viewModel.cropPoints.collectAsState()
    val category = viewModel.currentCaptureCategory

    // Title state
    val defaultTitle = remember {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val dateStr = sdf.format(Date())
        "${category}_$dateStr"
    }
    var title by remember { mutableStateOf(defaultTitle) }
    var notes by remember { mutableStateOf("") }

    // Cropped Base Bitmap (straightened)
    var croppedBaseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Active Filter preview bitmap
    var filteredBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeFilter by remember { mutableStateOf("ENHANCED") } // Default: Magic color enhance
    var isWarping by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    val filtersList = listOf(
        Pair("ORIGINAL", "Original"),
        Pair("ENHANCED", "Magic Color"),
        Pair("BW", "Photocopy B&W"),
        Pair("GRAYSCALE", "Grayscale"),
        Pair("SHARP", "Sharp Detail")
    )

    // Warping perspective crop asynchronously on enter
    LaunchedEffect(rawPath, cropPoints) {
        if (rawPath.isNotBlank()) {
            withContext(Dispatchers.Default) {
                val rawBmp = BitmapFactory.decodeFile(rawPath)
                if (rawBmp != null) {
                    val cropped = ImageUtils.perspectiveCrop(rawBmp, cropPoints)
                    rawBmp.recycle()
                    croppedBaseBitmap = cropped
                    // Apply default filter on top of the newly cropped base
                    filteredBitmap = ImageUtils.applyFilter(cropped, activeFilter)
                }
                isWarping = false
            }
        }
    }

    // Apply filters asynchronously on selection changes
    LaunchedEffect(activeFilter, croppedBaseBitmap) {
        val base = croppedBaseBitmap
        if (base != null) {
            withContext(Dispatchers.Default) {
                filteredBitmap = ImageUtils.applyFilter(base, activeFilter)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enhance Scan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Go Back")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Document Name & Notes Desk
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Document Title") },
                        leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    TextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Add custom notes or tag folder (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            }

            // Cropped Preview Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                val previewBmp = filteredBitmap
                if (isWarping) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Straightening skew & mapping corners...", color = Color.White, fontSize = 12.sp)
                    }
                } else if (previewBmp != null) {
                    Image(
                        bitmap = previewBmp.asImageBitmap(),
                        contentDescription = "Enhanced Scan Preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Error rendering straightened scan preview.", color = Color.LightGray)
                }
            }

            // Scrollable Filters Row
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "COLOR ENHANCEMENTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtersList) { (type, name) ->
                        val isSelected = activeFilter == type
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                .border(
                                    1.dp,
                                    if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { activeFilter = type }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Primary Save Button
            Button(
                onClick = {
                    isSaving = true
                    viewModel.processAndSaveDocument(
                        title = title,
                        filterType = activeFilter,
                        notes = notes,
                        onComplete = {
                            isSaving = false
                            onSaveSuccess()
                        }
                    )
                },
                enabled = !isWarping && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Saving high-quality scan...")
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Text("Finish & Save Scan", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
