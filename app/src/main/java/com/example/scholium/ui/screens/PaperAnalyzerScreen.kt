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
fun PaperAnalyzerScreen(
    chatDao: com.example.scholium.data.local.ChatDao,
    existingSessionId: Long? = null // <-- NEW: Accepts the ID from the History screen
) {
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

    // State for Processing Overlay (OCR Loading)
    var isProcessing by remember { mutableStateOf(false) }

    // State to force-clear the selection box
    var selectionResetKey by remember { mutableIntStateOf(0) }

    // --- CHAT & DATABASE STATES ---
    val chatHistory = remember { mutableStateListOf<com.example.scholium.domain.ChatMessage>() }
    var showChatSheet by remember { mutableStateOf(false) }
    var isAiThinking by remember { mutableStateOf(false) }
    var currentSessionId by remember { mutableStateOf<Long?>(existingSessionId) }

    fun loadPage(pageIndex: Int) {
        if (pdfFile != null && pageIndex in 0 until totalPageCount) {
            scope.launch(Dispatchers.IO) {
                val bitmap = PdfUtils.pdfToBitmap(pdfFile!!, pageIndex)
                launch(Dispatchers.Main) {
                    pdfBitmap = bitmap
                    currentPageIndex = pageIndex
                }
            }
        }
    }

    // --- NEW: RESTORE OLD SESSION IF LAUNCHED FROM HISTORY ---
    LaunchedEffect(existingSessionId) {
        if (existingSessionId != null && existingSessionId != -1L) {
            scope.launch(Dispatchers.IO) {
                val session = chatDao.getSession(existingSessionId)
                val pastMessages = chatDao.getMessagesForSession(existingSessionId)

                launch(Dispatchers.Main) {
                    // 1. Try to load the PDF File
                    if (session?.pdfPath != null) {
                        val file = File(session.pdfPath)
                        if (file.exists()) {
                            pdfFile = file
                            totalPageCount = PdfUtils.getPageCount(file)
                            loadPage(0)
                        } else {
                            android.widget.Toast.makeText(context, "Original PDF was moved or deleted. Please re-upload.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }

                    // 2. Load the Chat History
                    chatHistory.clear()
                    pastMessages.forEach { msg ->
                        chatHistory.add(com.example.scholium.domain.ChatMessage(msg.role, msg.content, msg.isUser))
                    }

                    // 3. Open the chat sheet automatically if we have history
                    if (chatHistory.isNotEmpty()) {
                        showChatSheet = true
                    }
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
                    launch(Dispatchers.Main) {
                        pdfFile = file
                        totalPageCount = count
                        loadPage(0)

                        // If they upload a NEW PDF, reset the chat session
                        currentSessionId = null
                        chatHistory.clear()
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
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            imageViewSize = coordinates.size.toSize()
                        }
                )

                // LAYER 2: The Drawing Overlay
                key(selectionResetKey) {
                    SelectionOverlay(
                        modifier = Modifier.fillMaxSize(),
                        onSelectionFinished = { rect: Rect ->
                            if (imageViewSize.width > 0 && imageViewSize.height > 0) {
                                val cropped = PdfUtils.cropBitmap(
                                    original = pdfBitmap!!,
                                    cropRect = rect,
                                    viewWidth = imageViewSize.width,
                                    viewHeight = imageViewSize.height
                                )
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
                            selectionResetKey++
                        },
                        onConfirm = { type ->
                            showDialog = false
                            selectionResetKey++
                            isProcessing = true

                            // RUN OCR AND START/CONTINUE CHAT
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val extractedText = OCRHelper.extractTextFromBitmap(currentCroppedBitmap!!)

                                    if (extractedText.trim().isEmpty()) {
                                        launch(Dispatchers.Main) {
                                            isProcessing = false
                                            android.widget.Toast.makeText(
                                                context,
                                                "Could not read any text. Please zoom in or select a clearer area.",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        return@launch
                                    }

                                    // Check if we need a new session or are appending to an active one
                                    var activeSessionId = currentSessionId
                                    if (activeSessionId == null || activeSessionId == -1L) {
                                        val newSession = com.example.scholium.data.local.ChatSessionEntity(
                                            title = "Analysis: $type",
                                            pdfPath = pdfFile?.absolutePath // Save the path!
                                        )
                                        activeSessionId = chatDao.insertSession(newSession)

                                        launch(Dispatchers.Main) {
                                            currentSessionId = activeSessionId
                                        }
                                    }

                                    val systemPrompt = "You are an academic assistant. Analyze this $type extracted from a paper: \"$extractedText\"."

                                    // Save messages to Database under the active session
                                    chatDao.insertMessage(com.example.scholium.data.local.ChatMessageEntity(sessionId = activeSessionId!!, role = "system", content = systemPrompt, isUser = false))
                                    chatDao.insertMessage(com.example.scholium.data.local.ChatMessageEntity(sessionId = activeSessionId!!, role = "user", content = "Explain this $type.", isUser = true))

                                    // Update UI on Main thread
                                    launch(Dispatchers.Main) {
                                        // Notice we do NOT clear the chat here! We append the new cropped context.
                                        chatHistory.add(com.example.scholium.domain.ChatMessage(role = "system", content = systemPrompt, isUser = false))
                                        chatHistory.add(com.example.scholium.domain.ChatMessage(role = "user", content = "Explain this $type.", isUser = true))

                                        showChatSheet = true
                                        isAiThinking = true
                                        isProcessing = false
                                    }

                                    // Call Sarvam AI
                                    val aiResponse = com.example.scholium.data.SarvamApiService.getChatResponse(chatHistory)

                                    // SAVE AI RESPONSE TO DB
                                    chatDao.insertMessage(com.example.scholium.data.local.ChatMessageEntity(sessionId = activeSessionId!!, role = "assistant", content = aiResponse, isUser = false))

                                    // Update UI with AI's answer
                                    launch(Dispatchers.Main) {
                                        chatHistory.add(com.example.scholium.domain.ChatMessage(role = "assistant", content = aiResponse, isUser = false))
                                        isAiThinking = false
                                    }

                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    launch(Dispatchers.Main) {
                                        isProcessing = false
                                        isAiThinking = false
                                    }
                                }
                            }
                        }
                    )
                }

                // LAYER 4: The Chat Sheet
                if (showChatSheet) {
                    com.example.scholium.ui.components.ChatSheet(
                        messages = chatHistory,
                        isThinking = isAiThinking,
                        onDismiss = { showChatSheet = false },
                        onSendMessage = { userText ->

                            chatHistory.add(com.example.scholium.domain.ChatMessage(role = "user", content = userText, isUser = true))
                            isAiThinking = true

                            scope.launch(Dispatchers.IO) {
                                try {
                                    // SAVE USER QUESTION TO DB
                                    currentSessionId?.let { id ->
                                        chatDao.insertMessage(com.example.scholium.data.local.ChatMessageEntity(sessionId = id, role = "user", content = userText, isUser = true))
                                    }

                                    val aiResponse = com.example.scholium.data.SarvamApiService.getChatResponse(chatHistory)

                                    // SAVE AI ANSWER TO DB
                                    currentSessionId?.let { id ->
                                        chatDao.insertMessage(com.example.scholium.data.local.ChatMessageEntity(sessionId = id, role = "assistant", content = aiResponse, isUser = false))
                                    }

                                    launch(Dispatchers.Main) {
                                        chatHistory.add(com.example.scholium.domain.ChatMessage(role = "assistant", content = aiResponse, isUser = false))
                                        isAiThinking = false
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    launch(Dispatchers.Main) { isAiThinking = false }
                                }
                            }
                        }
                    )
                }

                // LAYER 5: Full-Screen Loading Indicator
                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
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