package com.example.scholium.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimVerifierScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var claimInput by remember { mutableStateOf("") }
    var verifiedResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingStatus by remember { mutableStateOf("") }

    fun verifyClaim(claim: String) {
        if (claim.isBlank()) return
        isLoading = true
        verifiedResult = ""

        scope.launch(Dispatchers.IO) {
            try {

                launch(Dispatchers.Main) { loadingStatus = "Searching Crossref for evidence..." }

                val encodedClaim = URLEncoder.encode(claim, "UTF-8")
                val crossrefUrl = "https://api.crossref.org/works?query=$encodedClaim&select=title,author,URL,abstract&filter=has-abstract:true&rows=5"

                val httpClient = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .build()

                val crossrefReq = Request.Builder()
                    .url(crossrefUrl)
                    .addHeader("User-Agent", "ScholiumApp/1.0 (mailto:developer@scholium.app)")
                    .build()

                val crossrefResponse = httpClient.newCall(crossrefReq).execute()
                val crossrefBody = crossrefResponse.body?.string() ?: ""

                var evidenceText = ""

                if (crossrefResponse.isSuccessful) {
                    val itemsArray = JSONObject(crossrefBody).optJSONObject("message")?.optJSONArray("items")
                    if (itemsArray != null) {
                        for (i in 0 until itemsArray.length()) {
                            val paper = itemsArray.getJSONObject(i)
                            val title = paper.optJSONArray("title")?.optString(0, "Unknown Title") ?: "Unknown Title"
                            val url = paper.optString("URL", "No URL")

                            // Clean the abstract of Crossref's weird HTML tags
                            val rawAbstract = paper.optString("abstract", "No abstract available.")
                            val cleanAbstract = rawAbstract.replace(Regex("<.*?>"), "").trim()

                            evidenceText += "Paper ${i+1}:\nTitle: $title\nURL: $url\nAbstract: $cleanAbstract\n\n"
                        }
                    }
                }

                if (evidenceText.isBlank()) {
                    launch(Dispatchers.Main) {
                        verifiedResult = "Could not find any relevant papers on Crossref to verify this claim."
                        isLoading = false
                    }
                    return@launch
                }

                launch(Dispatchers.Main) { loadingStatus = "AI is verifying claims against literature..." }

                val systemPrompt = """
                    You are an academic verification agent. The user will provide a draft claim, and I will provide abstracts from 3 real research papers.
                    Your job is to:
                    1. Evaluate if the papers support, contradict, or add nuance to the user's claim.
                    2. Rewrite the user's claim to be scientifically accurate based ONLY on the provided evidence.
                    3. Add inline citations (e.g., [1], [2]) directly into your rewritten text.
                    4. Provide a 'Bibliography' section at the bottom linking to the URLs provided.
                    If the evidence does not support the claim, explicitly state that the claim is unsupported.
                    Send plain text only. DO NOT use characters like *, # and -
                """.trimIndent()

                val userPrompt = "USER'S CLAIM:\n$claim\n\nEVIDENCE FROM CROSSREF:\n$evidenceText"

                val partsArray = JSONArray()
                partsArray.put(JSONObject().apply { put("text", "$systemPrompt\n\n$userPrompt") })

                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply { put("parts", partsArray) }))
                }.toString()

                val geminiApiKey = BuildConfig.GEMINI_API_KEY
                val geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiApiKey"

                val geminiReq = Request.Builder()
                    .url(geminiUrl)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(geminiReq).execute().use { response ->
                    val responseBody = response.body?.string() ?: "Empty response"
                    if (response.isSuccessful) {
                        val content = JSONObject(responseBody)
                            .optJSONArray("candidates")?.optJSONObject(0)
                            ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                            ?.optString("text", "Failed to generate verification.") ?: "Parse error."

                        launch(Dispatchers.Main) { verifiedResult = content.trim() }
                    } else {
                        launch(Dispatchers.Main) { verifiedResult = "Gemini API Error ${response.code}: $responseBody" }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) { verifiedResult = "Network Error: ${e.message}" }
            } finally {
                launch(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Autonomous Claim Verifier", color = MaterialTheme.colorScheme.onPrimary) },
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
                text = "Paste a draft paragraph or a bold claim. The agent will hunt down real papers on Crossref, verify the facts, and inject inline citations.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = claimInput,
                onValueChange = { claimInput = it },
                label = { Text("Enter your draft claim...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 10
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { verifyClaim(claimInput) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading && claimInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(loadingStatus, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.FactCheck, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify & Cite", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (verifiedResult.isNotEmpty() && !isLoading) {
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
                            Text("Verified Text & Bibliography", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(verifiedResult)) }) {
                                Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(20.dp))
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        SelectionContainer {
                            Text(text = verifiedResult, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}