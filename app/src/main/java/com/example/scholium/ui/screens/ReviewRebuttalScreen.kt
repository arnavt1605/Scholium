package com.example.scholium.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.scholium.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewRebuttalScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var reviewerComments by remember { mutableStateOf("") }
    var rebuttalResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingStatus by remember { mutableStateOf("") }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedUri = uri
    }

    fun generateRebuttal() {
        if (selectedUri == null || reviewerComments.isBlank()) return
        isLoading = true
        rebuttalResult = ""
        loadingStatus = "Scanning manuscript..."

        scope.launch(Dispatchers.IO) {
            try {
                val partsArray = JSONArray()

                val systemPrompt = """
                    You are an expert academic co-author helping to draft a 'Response to Reviewers' document. 
                    I have attached our manuscript (PDF) and the reviewer's questions/critiques below.
                    
                    Please go through the reviewer's comments and systematically address them ONE by ONE in a numbered list (e.g., Q1, A1).
                    
                    For EACH question:
                    1. If the manuscript contains evidence or data to rebut the critique, draft a professional, polite explanation citing the relevant concepts or sections from the paper.
                    2. If the manuscript DOES NOT address the query, DO NOT hallucinate an answer. Explicitly state: "Status: Not Addressed. You will need to rectify the manuscript or add new data to answer this."
                    
                    REVIEWER COMMENTS:
                    $reviewerComments
                """.trimIndent()

                partsArray.put(JSONObject().apply { put("text", systemPrompt) })

                // Attach the PDF natively
                val inputStream = context.contentResolver.openInputStream(selectedUri!!)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null) {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val inlineDataObj = JSONObject().apply {
                        put("mime_type", "application/pdf")
                        put("data", base64)
                    }
                    partsArray.put(JSONObject().apply { put("inline_data", inlineDataObj) })
                }

                launch(Dispatchers.Main) { loadingStatus = "Cross-referencing critiques with paper..." }

                val contentObj = JSONObject().apply { put("parts", partsArray) }
                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().put(contentObj))
                }.toString()

                val geminiApiKey = BuildConfig.GEMINI_API_KEY
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiApiKey"

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(180, TimeUnit.SECONDS) // Generous timeout for reading the PDF
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
                        val content = jsonResponse.optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text", "Failed to generate response.") ?: "Parse error."

                        launch(Dispatchers.Main) { rebuttalResult = content.trim() }
                    } else {
                        launch(Dispatchers.Main) { rebuttalResult = "API Error ${response.code}: $responseBody" }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) { rebuttalResult = "Network Error: ${e.message}" }
            } finally {
                launch(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rebuttal Drafter", color = MaterialTheme.colorScheme.onPrimary) },
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
                text = "Upload your manuscript and paste the reviewer's critiques. The AI will cross-reference the paper to draft professional rebuttals or identify missing data.",
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
                Text(if (selectedUri != null) "Manuscript Selected (Tap to change)" else "Upload Manuscript (PDF)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = reviewerComments,
                onValueChange = { reviewerComments = it },
                label = { Text("Paste Reviewer Comments here...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp),
                maxLines = 10
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { generateRebuttal() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading && selectedUri != null && reviewerComments.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSecondary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(loadingStatus, color = MaterialTheme.colorScheme.onSecondary)
                } else {
                    Icon(Icons.Default.RateReview, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Draft Response", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (rebuttalResult.isNotEmpty() && !isLoading) {
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
                            Text("Rebuttal Draft", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(rebuttalResult)) }) {
                                Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(20.dp))
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        SelectionContainer {
                            Text(text = rebuttalResult, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}