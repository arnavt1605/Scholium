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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

// 1. We create a Data Class to hold the structured JSON data
data class JournalRecommendation(
    val name: String,
    val publisher: String,
    val metrics: String,
    val justification: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalMatcherScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    // 2. We now store a List of objects instead of a single String
    var matchResults by remember { mutableStateOf<List<JournalRecommendation>>(emptyList()) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingStatus by remember { mutableStateOf("") }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedUri = uri
    }

    fun findJournals() {
        if (selectedUri == null) return
        isLoading = true
        matchResults = emptyList()
        errorMessage = ""
        loadingStatus = "Reading manuscript..."

        scope.launch(Dispatchers.IO) {
            try {
                val partsArray = JSONArray()

                // 3. The Strict JSON Prompt
                val promptText = """
                    You are an expert academic publishing strategist. Read the attached manuscript and recommend 7 to 8 best-fit academic journals for submission.
                    
                    You MUST return the result strictly as a JSON array of objects. Do not use markdown blocks.
                    Each object must have exactly these keys:
                    "name": (String) Journal Name
                    "publisher": (String) Publisher Name (e.g., IEEE, Nature)
                    "metrics": (String) Estimated Impact Factor and Quartile
                    "justification": (String) Why this paper is a perfect fit for this journal's scope
                """.trimIndent()

                partsArray.put(JSONObject().apply { put("text", promptText) })

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

                launch(Dispatchers.Main) { loadingStatus = "Analyzing scope and matching 8 journals..." }

                val contentObj = JSONObject().apply { put("parts", partsArray) }

                // 4. Force Gemini to output raw JSON using generationConfig
                val genConfig = JSONObject().apply { put("response_mime_type", "application/json") }
                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().put(contentObj))
                    put("generationConfig", genConfig)
                }.toString()

                val geminiApiKey = BuildConfig.GEMINI_API_KEY
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
                        val contentString = jsonResponse.optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text", "[]") ?: "[]"

                        // 5. Parse the JSON Array back into Kotlin Objects
                        val parsedList = mutableListOf<JournalRecommendation>()
                        try {
                            val cleanJson = contentString.replace("```json", "").replace("```", "").trim()
                            val jsonArr = JSONArray(cleanJson)
                            for (i in 0 until jsonArr.length()) {
                                val obj = jsonArr.getJSONObject(i)
                                parsedList.add(
                                    JournalRecommendation(
                                        name = obj.optString("name", "Unknown Journal"),
                                        publisher = obj.optString("publisher", "Unknown Publisher"),
                                        metrics = obj.optString("metrics", "N/A"),
                                        justification = obj.optString("justification", "")
                                    )
                                )
                            }
                            launch(Dispatchers.Main) { matchResults = parsedList }
                        } catch (e: Exception) {
                            launch(Dispatchers.Main) { errorMessage = "Failed to parse AI output." }
                        }
                    } else {
                        launch(Dispatchers.Main) { errorMessage = "API Error ${response.code}" }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) { errorMessage = "Network Error: ${e.message}" }
            } finally {
                launch(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal Matcher", color = MaterialTheme.colorScheme.onPrimary) },
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
                text = "Upload your draft manuscript. AI will analyze your methodology and recommend 7-8 of the best Q1/Q2 journals to submit to.",
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
                Text(if (selectedUri != null) "Select Different Draft" else "Upload Manuscript")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { findJournals() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading && selectedUri != null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSecondary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(loadingStatus, color = MaterialTheme.colorScheme.onSecondary)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Find Target Journals", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 6. The Beautiful New Card Layout
            if (matchResults.isNotEmpty() && !isLoading) {
                Text(
                    text = "Top Recommendations",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(16.dp))

                SelectionContainer {
                    Column {
                        matchResults.forEachIndexed { index, journal ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${index + 1}. ${journal.name}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "Publisher: ${journal.publisher}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Colored Badge for Metrics
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = journal.metrics,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(text = journal.justification, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}