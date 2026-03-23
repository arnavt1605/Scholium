package com.example.scholium.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
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
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperReviewerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var reviewResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingStatus by remember { mutableStateOf("") }

    // --- UTILITY: PDF to Base64 Images ---
    fun extractPagesAsBase64(uri: Uri, maxPages: Int = 3): List<String> {
        val base64Images = mutableListOf<String>()
        try {
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
            val pdfRenderer = PdfRenderer(parcelFileDescriptor)

            val pageCount = minOf(pdfRenderer.pageCount, maxPages)

            for (i in 0 until pageCount) {
                val page = pdfRenderer.openPage(i)
                // Render at a lower resolution to save memory and API payload size (e.g., 800px wide)
                val scale = 800f / page.width
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // Fill background with white (otherwise transparent PDFs turn black)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                // Compress to JPEG and convert to Base64
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                val byteArray = outputStream.toByteArray()
                val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                base64Images.add(base64String)
                page.close()
            }
            pdfRenderer.close()
            parcelFileDescriptor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return base64Images
    }


    // --- NETWORK: Google Gemini Vision API ---
    fun runVisionReview(uri: Uri) {
        isLoading = true
        reviewResult = ""
        loadingStatus = "Extracting images from PDF..."

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Convert first 3 pages to images
                val base64Pages = extractPagesAsBase64(uri, maxPages = 3)
                if (base64Pages.isEmpty()) {
                    launch(Dispatchers.Main) {
                        reviewResult = "Error: Could not read the PDF file."
                        isLoading = false
                    }
                    return@launch
                }

                launch(Dispatchers.Main) { loadingStatus = "Analyzing charts, tables, and text with Gemini Vision..." }

                // 2. Build the Gemini API JSON Payload
                val partsArray = JSONArray()

                // Add the Prompt Text
                val textPart = JSONObject().apply {
                    put("text", "You are 'Reviewer 2', an expert academic peer reviewer. I am providing you with images of the first few pages of a manuscript (including figures and tables). Please provide a structured review: \n1. Core Strengths\n2. Potential Weaknesses or Flaws\n3. Constructive suggestions for improvement. Send plain text and do not use special characters like #,- or *")
                }
                partsArray.put(textPart)

                // Add each page as an Inline Data part
                for (base64 in base64Pages) {
                    val inlineDataObj = JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", base64)
                    }
                    val imagePart = JSONObject().apply {
                        put("inline_data", inlineDataObj)
                    }
                    partsArray.put(imagePart)
                }

                val contentObj = JSONObject().apply {
                    put("parts", partsArray)
                }

                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().put(contentObj))
                }.toString()

                // 3. Fire the Request
                val geminiApiKey = "AIzaSyDtSKxzP5KOS8rThuWomehwg1DQPT1yF0Q"
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiApiKey"

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: "Empty response"
                    if (response.isSuccessful) {
                        val jsonResponse = JSONObject(responseBody)
                        val candidates = jsonResponse.optJSONArray("candidates")
                        val content = candidates?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text", "No review generated.") ?: "Parse error."

                        launch(Dispatchers.Main) { reviewResult = content.trim() }
                    } else {
                    val errorCode = response.code
                    launch(Dispatchers.Main) { reviewResult = "API Error $errorCode: $responseBody" }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) { reviewResult = "Network Error: ${e.message}" }
            } finally {
                launch(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Get a basic file name for the UI
            selectedFileName = "Manuscript_Selected.pdf"
            runVisionReview(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Paper Reviewer", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Upload your manuscript",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { pdfPickerLauncher.launch("application/pdf") },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isLoading
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedFileName != null) "Select Different PDF" else "Upload Manuscript (PDF)")
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(loadingStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            }

            if (reviewResult.isNotEmpty() && !isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Expert Review", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(reviewResult)) }) {
                                Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(20.dp))
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        SelectionContainer {
                            Text(text = reviewResult, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}