package com.example.scholium.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
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
import com.example.scholium.BuildConfig
import com.example.scholium.ui.components.RegionSelectionDialog
import com.example.scholium.ui.components.SelectionOverlay
import com.example.scholium.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperAnalyzerScreen(
    chatDao: com.example.scholium.data.local.ChatDao,
    existingSessionId: Long? = null
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

    // State for Processing Overlay
    var isProcessing by remember { mutableStateOf(false) }
    var selectionResetKey by remember { mutableIntStateOf(0) }

    // Chat States
    val chatHistory = remember { mutableStateListOf<com.example.scholium.domain.ChatMessage>() }
    var showChatSheet by remember { mutableStateOf(false) }
    var isAiThinking by remember { mutableStateOf(false) }
    var currentSessionId by remember { mutableStateOf<Long?>(existingSessionId) }

    val httpClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

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

    LaunchedEffect(existingSessionId) {
        if (existingSessionId != null && existingSessionId != -1L) {
            scope.launch(Dispatchers.IO) {
                val session = chatDao.getSession(existingSessionId)
                val pastMessages = chatDao.getMessagesForSession(existingSessionId)

                launch(Dispatchers.Main) {
                    if (session?.pdfPath != null) {
                        val file = File(session.pdfPath)
                        if (file.exists()) {
                            pdfFile = file
                            totalPageCount = PdfUtils.getPageCount(file)
                            loadPage(0)
                        } else {
                            android.widget.Toast.makeText(context, "Original PDF was moved or deleted.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }

                    chatHistory.clear()
                    pastMessages.forEach { msg ->
                        // Filter out old system prompts to keep the UI clean
                        if (msg.role != "system") {
                            chatHistory.add(com.example.scholium.domain.ChatMessage(msg.role, msg.content, msg.isUser))
                        }
                    }

                    if (chatHistory.isNotEmpty()) showChatSheet = true
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
                        currentSessionId = null
                        chatHistory.clear()
                    }
                }
            }
        }
    }

    // --- GEMINI NETWORK CALLS ---
    suspend fun analyzeImageWithGemini(bitmap: Bitmap, type: String): String {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                val promptText = "You are an expert academic assistant. Analyze the specific $type captured in this image from a research paper. Explain it clearly."

                val partsArray = JSONArray()
                partsArray.put(JSONObject().apply { put("text", promptText) })
                partsArray.put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", base64Image)
                    })
                })

                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply { put("parts", partsArray) }))
                }.toString()

                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)
                            ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                            ?.optString("text", "No analysis provided.") ?: "Parse error."
                    } else {
                        "API Error: ${response.code}"
                    }
                }
            } catch (e: Exception) {
                "Network Error: ${e.message}"
            }
        }
    }

    suspend fun continueChatWithGemini(history: List<com.example.scholium.domain.ChatMessage>, newMessage: String): String {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val contentsArray = JSONArray()

                // Add previous context to the payload
                for (msg in history) {
                    val role = if (msg.isUser) "user" else "model"
                    contentsArray.put(JSONObject().apply {
                        put("role", role)
                        put("parts", JSONArray().put(JSONObject().apply { put("text", msg.content) }))
                    })
                }

                // Add the new message
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().apply { put("text", newMessage) }))
                })

                val jsonBody = JSONObject().apply { put("contents", contentsArray) }.toString()
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)
                            ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                            ?.optString("text", "Failed to generate answer.") ?: "Parse error."
                    } else {
                        "API Error: ${response.code}"
                    }
                }
            } catch (e: Exception) {
                "Network Error: ${e.message}"
            }
        }
    }

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
                BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { loadPage(currentPageIndex - 1) }, enabled = currentPageIndex > 0) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
                        }
                        Text("Page ${currentPageIndex + 1} of $totalPageCount", style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { loadPage(currentPageIndex + 1) }, enabled = currentPageIndex < totalPageCount - 1) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (pdfBitmap != null) {
                Image(
                    bitmap = pdfBitmap!!.asImageBitmap(),
                    contentDescription = "PDF Page",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                        imageViewSize = coordinates.size.toSize()
                    }
                )

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

                            scope.launch {
                                val aiResponse = analyzeImageWithGemini(currentCroppedBitmap!!, type)

                                var activeSessionId = currentSessionId
                                if (activeSessionId == null || activeSessionId == -1L) {
                                    val newSession = com.example.scholium.data.local.ChatSessionEntity(
                                        title = "Analysis: $type",
                                        pdfPath = pdfFile?.absolutePath
                                    )
                                    activeSessionId = chatDao.insertSession(newSession)
                                    currentSessionId = activeSessionId
                                }

                                val userPrompt = "[Image Cropped] Please analyze this $type."

                                chatDao.insertMessage(com.example.scholium.data.local.ChatMessageEntity(sessionId = activeSessionId!!, role = "user", content = userPrompt, isUser = true))
                                chatDao.insertMessage(com.example.scholium.data.local.ChatMessageEntity(sessionId = activeSessionId!!, role = "assistant", content = aiResponse, isUser = false))

                                chatHistory.add(com.example.scholium.domain.ChatMessage(role = "user", content = userPrompt, isUser = true))
                                chatHistory.add(com.example.scholium.domain.ChatMessage(role = "assistant", content = aiResponse, isUser = false))

                                showChatSheet = true
                                isProcessing = false
                            }
                        }
                    )
                }

                if (showChatSheet) {
                    com.example.scholium.ui.components.ChatSheet(
                        messages = chatHistory,
                        isThinking = isAiThinking,
                        onDismiss = { showChatSheet = false },
                        onSendMessage = { userText ->
                            isAiThinking = true

                            scope.launch {
                                val currentHistory = chatHistory.toList() // Snapshot before adding
                                chatHistory.add(com.example.scholium.domain.ChatMessage(role = "user", content = userText, isUser = true))

                                currentSessionId?.let { id ->
                                    chatDao.insertMessage(com.example.scholium.data.local.ChatMessageEntity(sessionId = id, role = "user", content = userText, isUser = true))
                                }

                                val aiResponse = continueChatWithGemini(currentHistory, userText)

                                currentSessionId?.let { id ->
                                    chatDao.insertMessage(com.example.scholium.data.local.ChatMessageEntity(sessionId = id, role = "assistant", content = aiResponse, isUser = false))
                                }

                                chatHistory.add(com.example.scholium.domain.ChatMessage(role = "assistant", content = aiResponse, isUser = false))
                                isAiThinking = false
                            }
                        }
                    )
                }

                if (isProcessing) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(100.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No papers yet", style = MaterialTheme.typography.headlineSmall, color = Color.Gray)
                    Text("Tap + to analyze a paper.", color = Color.Gray)
                }
            }
        }
    }
}