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
import androidx.compose.material.icons.filled.LibraryBooks
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
fun LitReviewerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var reviewResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingStatus by remember { mutableStateOf("") }

    // Launcher to select multiple PDFs
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->

        selectedUris = uris.take(4)
    }

    fun runLiteratureReview() {
        if (selectedUris.isEmpty()) return
        isLoading = true
        reviewResult = ""
        loadingStatus = "Reading ${selectedUris.size} papers..."

        scope.launch(Dispatchers.IO) {
            try {
                val partsArray = JSONArray()

                // 1. Add the highly specific Agentic Prompt
                val promptText = """
                    You are an expert academic researcher and synthesis engine. I am providing you with ${selectedUris.size} full research papers. 
                    Your task is to write a comprehensive 'Literature Review' synthesizing their findings. 
                    Format the output with the following Markdown headings:
                    - Introduction & Common Themes
                    - Methodological Comparisons
                    - Consensus & Contradictions (Where do they agree/disagree?)
                    - The Research Gap (What is left unanswered?)
                    Be highly analytical, cite specific details from the texts, and maintain a formal academic tone. Send only plain text. Do not include characters like #, * and -.
                """.trimIndent()

                partsArray.put(JSONObject().apply { put("text", promptText) })

                // 2. Loop through the selected PDFs and attach them natively
                for (uri in selectedUris) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    // Read the raw PDF file bytes
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
                }

                launch(Dispatchers.Main) { loadingStatus = "Synthesizing literature review..." }

                val contentObj = JSONObject().apply { put("parts", partsArray) }
                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().put(contentObj))
                }.toString()

                val geminiApiKey = BuildConfig.GEMINI_API_KEY
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiApiKey"

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    // Give it 3 minutes! Reading 4 full PDFs takes heavy computation
                    .readTimeout(180, TimeUnit.SECONDS)
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
                            ?.optString("text", "No review generated.") ?: "Parse error."

                        launch(Dispatchers.Main) { reviewResult = content.trim() }
                    } else {
                        launch(Dispatchers.Main) { reviewResult = "API Error ${response.code}: $responseBody" }
                    }
                }
            } catch (e: OutOfMemoryError) {
                launch(Dispatchers.Main) { reviewResult = "Error: The selected PDFs are too large for your phone's memory. Try selecting fewer or smaller files." }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) { reviewResult = "Network Error: ${e.message}" }
            } finally {
                launch(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Autonomous Litearture Reviewer", color = MaterialTheme.colorScheme.onPrimary) },
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
                text = "Select up to 3 related research papers. The AI agent will read all of them and automatically write a synthesized literature review.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isLoading
            ) {
                Icon(Icons.Default.LibraryBooks, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select PDFs (Max 3)")
            }

            if (selectedUris.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${selectedUris.size} PDF(s) selected ready for analysis.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { runLiteratureReview() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading && selectedUris.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSecondary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(loadingStatus, color = MaterialTheme.colorScheme.onSecondary)
                } else {
                    Text("Generate Literature Review", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

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
                            Text("Synthesized Review", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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