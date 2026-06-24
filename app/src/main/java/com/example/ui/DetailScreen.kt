package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.data.Document
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val document by viewModel.activeDocument.collectAsState()
    val ocrProcessingIds by viewModel.ocrProcessingIds.collectAsState()

    if (document == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Document not found.")
        }
        return
    }

    val doc = document!!
    val isOcrRunning = ocrProcessingIds.contains(doc.id)

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Scan Page", "AI OCR Text", "Notes & Tags")

    // Local editable fields
    var editableTitle by remember(doc.id) { mutableStateOf(doc.title) }
    var editableCategory by remember(doc.id) { mutableStateOf(doc.category) }
    var editableNotes by remember(doc.id) { mutableStateOf(doc.notes) }
    var editableOcrText by remember(doc.ocrText) { mutableStateOf(doc.ocrText) }

    var isEditingDetails by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val categories = listOf("Document", "Receipt", "Business Card", "Whiteboard", "Note")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = doc.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Document", tint = MaterialTheme.colorScheme.error)
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
            // Tab Header Row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(label, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Tab Content Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> {
                        // --- TAB 1: Scan Page ---
                        val pages = doc.getProcessedImagePaths()
                        val pagerState = rememberPagerState(pageCount = { pages.size })
                        val coroutineScope = rememberCoroutineScope()

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { pageIndex ->
                                val file = File(pages[pageIndex])
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (file.exists()) {
                                        AsyncImage(
                                            model = file,
                                            contentDescription = "Enhanced Scan image - Page ${pageIndex + 1}",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Text("Image file missing.", color = Color.White)
                                    }
                                }
                            }

                            // Page Indicator Dots Overlay
                            if (pages.size > 1) {
                                Row(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 16.dp)
                                        .background(Color(0x99000000), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(pages.size) { iteration ->
                                        val color = if (pagerState.currentPage == iteration) Color(0xFF38BDF8) else Color.Gray.copy(alpha = 0.5f)
                                        Box(
                                            modifier = Modifier
                                                .padding(2.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .size(8.dp)
                                        )
                                    }
                                }

                                // Left/Right Page Indicators Overlay
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left Arrow
                                    if (pagerState.currentPage > 0) {
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                }
                                            },
                                            modifier = Modifier.background(Color(0x66000000), CircleShape)
                                        ) {
                                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous Page", tint = Color.White)
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(48.dp))
                                    }

                                    // Right Arrow
                                    if (pagerState.currentPage < pages.size - 1) {
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            },
                                            modifier = Modifier.background(Color(0x66000000), CircleShape)
                                        ) {
                                            Icon(Icons.Filled.ChevronRight, contentDescription = "Next Page", tint = Color.White)
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(48.dp))
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // --- TAB 2: Extracted OCR Text ---
                        if (isOcrRunning) {
                            // Glowing AI Running visualizer
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0F172A)),
                                contentAlignment = Alignment.Center
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "ocr_beam")
                                val beamOffset by infiniteTransition.animateFloat(
                                    initialValue = 0.2f,
                                    targetValue = 0.8f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "ocr_beam_offset"
                                )

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val currentY = this.size.height * beamOffset
                                    drawLine(
                                        color = Color(0xFF38BDF8),
                                        start = Offset(0f, currentY),
                                        end = Offset(this.size.width, currentY),
                                        strokeWidth = 4.dp.toPx()
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                                    Text(
                                        "AI OCR Magic Transcribing...",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        "Gemini is analyzing formatting, tables, lists, and reading handwritten notes to convert your document into fully editable content.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else if (doc.ocrText.isBlank()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentPasteOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    Text("No OCR text detected", fontWeight = FontWeight.Bold)
                                    Text(
                                        "Either the file contained no readable text, or the Gemini OCR pipeline was bypassed.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // Text Editor Pane
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "EDITABLE TEXT",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Save edits button
                                        IconButton(
                                            onClick = {
                                                viewModel.updateActiveDocumentOcrText(editableOcrText)
                                                Toast.makeText(context, "OCR text saved!", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Filled.Save, contentDescription = "Save Edits", tint = MaterialTheme.colorScheme.primary)
                                        }

                                        // Copy to clipboard
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("OCR Text", editableOcrText)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Text copied to clipboard!", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Text")
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = editableOcrText,
                                    onValueChange = { editableOcrText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                            }
                        }
                    }
                    2 -> {
                        // --- TAB 3: Notes & Metadata ---
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
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
                                        Text("DOCUMENT PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                                        OutlinedTextField(
                                            value = editableTitle,
                                            onValueChange = {
                                                editableTitle = it
                                                viewModel.updateActiveDocumentDetails(it, editableCategory, editableNotes)
                                            },
                                            label = { Text("Title") },
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Category", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(categories) { cat ->
                                                    val isSelected = editableCategory == cat
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = {
                                                            editableCategory = cat
                                                            viewModel.updateActiveDocumentDetails(editableTitle, cat, editableNotes)
                                                        },
                                                        label = { Text(cat) }
                                                    )
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = editableNotes,
                                            onValueChange = {
                                                editableNotes = it
                                                viewModel.updateActiveDocumentDetails(editableTitle, editableCategory, it)
                                            },
                                            label = { Text("Custom Notes / Tags") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 3
                                        )
                                    }
                                }
                            }

                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("METRICS & SYNC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Backup Status", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (doc.isSynced) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                                    contentDescription = null,
                                                    tint = if (doc.isSynced) Color(0xFF10B981) else MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = if (doc.isSynced) "Synced to Cloud" else "Local Only",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (doc.isSynced) Color(0xFF10B981) else MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Enhancement Color Filter", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                            Text(doc.filterType, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        val dateStr = remember(doc.timestamp) {
                                            val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                                            sdf.format(Date(doc.timestamp))
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Captured", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                            Text(dateStr, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Export action drawer at bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "EXPORT & SHARE OPTIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        AssistChip(
                            onClick = { viewModel.exportAsPdf(doc) },
                            label = { Text("Export PDF") },
                            leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = Color(0xFFEF4444)) }
                        )
                    }

                    item {
                        AssistChip(
                            onClick = { viewModel.exportAsWord(doc) },
                            label = { Text("Export Word (.doc)") },
                            leadingIcon = { Icon(Icons.Outlined.Article, contentDescription = null, tint = Color(0xFF3B82F6)) }
                        )
                    }

                    item {
                        AssistChip(
                            onClick = { viewModel.exportAsPowerPoint(doc) },
                            label = { Text("Export PPT Layout") },
                            leadingIcon = { Icon(Icons.Outlined.Slideshow, contentDescription = null, tint = Color(0xFFF97316)) }
                        )
                    }

                    item {
                        AssistChip(
                            onClick = { viewModel.shareImage(doc) },
                            label = { Text("Share JPG Image") },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Scan?") },
            text = { Text("Are you sure you want to permanently delete \"${doc.title}\"? This action is irreversible.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(doc)
                        showDeleteConfirm = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
