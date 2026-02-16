package com.example.scholium.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.example.scholium.ui.components.RegionSelectionDialog
import com.example.scholium.ui.components.SelectionOverlay
import com.example.scholium.utils.OCRHelper
import com.example.scholium.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperAnalyzerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pdfFile by remember { mutableStateOf<File?>(null) }
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var totalPageCount by remember { mutableIntStateOf(0) }

    // State for Cropping & Dialog
    var showDialog by remember { mutableStateOf(false) }
    var currentCroppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageViewSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    // State for Processing (OCR)
    var isProcessing by remember { mutableStateOf(false) }

    // State to force-clear the selection box (The Fix for your bug)
    var selectionResetKey by remember { mutableIntStateOf(0) }

    fun loadPage(pageIndex: Int) {
        if (pdfFile != null && pageIndex in 0 until totalPageCount) {
            scope.launch(Dispatchers.IO) {
                val bitmap = PdfUtils.pdfToBitmap(pdfFile!!, pageIndex)
                // Switch back to Main thread to update UI
                launch(Dispatchers.Main) {
                    pdfBitmap = bitmap
                    currentPageIndex = pageIndex
                }
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val file = PdfUtils.uriToFile(context, it)
                if (file != null) {
                    val count = PdfUtils.getPageCount(file)
                    // Update State on Main Thread
                    launch(Dispatchers.Main) {
                        pdfFile = file
                        totalPageCount = count
                        loadPage(0)
                    }
                }
            }
        }
    }

    // UI structure
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scholium", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { pdfPickerLauncher.launch("application/pdf") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Upload PDF", tint = Color.White)
            }
        },
        bottomBar = {
            if (pdfBitmap != null) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { loadPage(currentPageIndex - 1) },
                            enabled = currentPageIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
                        }

                        Text(
                            text = "Page ${currentPageIndex + 1} of $totalPageCount",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        IconButton(
                            onClick = { loadPage(currentPageIndex + 1) },
                            enabled = currentPageIndex < totalPageCount - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->

        // --- MAIN CONTENT AREA ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (pdfBitmap != null) {
                // LAYER 1: The PDF Page Image
                Image(
                    bitmap = pdfBitmap!!.asImageBitmap(),
                    contentDescription = "PDF Page",
                    // FillBounds ensures the image stretches to fill the space, simplifying coordinate math
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            // Capture the actual size of the image on screen for math later
                            imageViewSize = coordinates.size.toSize()
                        }
                )

                // LAYER 2: The Drawing Overlay
                // 'key' forces this component to restart when selectionResetKey changes
                key(selectionResetKey) {
                    SelectionOverlay(
                        modifier = Modifier.fillMaxSize(),
                        onSelectionFinished = { rect: Rect ->
                            // MATH: Crop the actual bitmap based on where user drew on screen
                            if (imageViewSize.width > 0 && imageViewSize.height > 0) {
                                val cropped = PdfUtils.cropBitmap(
                                    original = pdfBitmap!!,
                                    cropRect = rect,
                                    viewWidth = imageViewSize.width,
                                    viewHeight = imageViewSize.height
                                )
                                // If crop was successful, show the popup
                                if (cropped != null) {
                                    currentCroppedBitmap = cropped
                                    showDialog = true
                                }
                            }
                        }
                    )
                }

                // LAYER 3: The Popup Dialog (Conditional)
                if (showDialog && currentCroppedBitmap != null) {
                    RegionSelectionDialog(
                        croppedBitmap = currentCroppedBitmap!!,
                        onDismiss = {
                            showDialog = false
                            selectionResetKey++ // FIX: Increment key to wipe the blue box on Cancel
                        },
                        onConfirm = { type ->
                            showDialog = false
                            selectionResetKey++ // FIX: Increment key to wipe the blue box on Confirm
                            isProcessing = true // Start Loading

                            // RUN OCR
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val extractedText = OCRHelper.extractTextFromBitmap(currentCroppedBitmap!!)

                                    println("=== OCR RESULT ===")
                                    println("Type: $type")
                                    println("Text: $extractedText")
                                    println("==================")

                                    // TODO: Next Phase - Send this to Gemini AI

                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    launch(Dispatchers.Main) {
                                        isProcessing = false // Stop Loading
                                    }
                                }
                            }
                        }
                    )
                }

                // LAYER 4: Loading Indicator
                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)), // Dim the background
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

            } else {
                // Empty placeholder
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No papers yet", style = MaterialTheme.typography.headlineSmall, color = Color.Gray)
                    Text("Tap + to analyze a paper.", color = Color.Gray)
                }
            }
        }
    }
}