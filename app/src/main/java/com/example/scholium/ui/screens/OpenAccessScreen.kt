package com.example.scholium.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

// Data class to hold the Unpaywall result
data class OAResult(
    val title: String,
    val journal: String,
    val isOa: Boolean,
    val pdfUrl: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenAccessScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var doiInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<OAResult?>(null) }

    fun checkOpenAccess(doi: String) {
        if (doi.isBlank()) return
        isLoading = true
        errorMessage = ""
        result = null

        scope.launch(Dispatchers.IO) {
            try {
                // Clean the input: Remove standard URL prefixes if the user pasted a full link
                val cleanDoi = doi.replace("https://doi.org/", "")
                    .replace("http://dx.doi.org/", "")
                    .trim()

                // Unpaywall requires an email parameter as their "API Key"
                val url = "https://api.unpaywall.org/v2/$cleanDoi?email=developer@scholium.app"

                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        val jsonObject = JSONObject(responseBody)

                        val isOa = jsonObject.getBoolean("is_oa")
                        val title = jsonObject.optString("title", "Unknown Title")
                        val journal = jsonObject.optString("journal_name", "Unknown Journal")

                        var pdfUrl: String? = null

                        if (isOa) {
                            val bestLocation = jsonObject.optJSONObject("best_oa_location")
                            // Try to get the direct PDF link, otherwise fallback to the landing page URL
                            pdfUrl = bestLocation?.optString("url_for_pdf", null)
                                ?: bestLocation?.optString("url", null)
                        }

                        launch(Dispatchers.Main) {
                            result = OAResult(title, journal, isOa, pdfUrl)
                        }
                    } else if (response.code == 404) {
                        launch(Dispatchers.Main) { errorMessage = "DOI not found in the Unpaywall database." }
                    } else {
                        launch(Dispatchers.Main) { errorMessage = "API Error ${response.code}: Could not fetch data." }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) { errorMessage = "Network Error: Check your connection." }
            } finally {
                launch(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open Access Finder", color = MaterialTheme.colorScheme.onPrimary) },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Hit a paywall? Paste the DOI below to check if a free, legal PDF exists in a university repository.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = doiInput,
                onValueChange = { doiInput = it },
                label = { Text("DOI (e.g., 10.1038/nature12373)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { checkOpenAccess(doiInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && doiInput.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Search for Free PDF")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Error State
            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }

            // Result State
            result?.let { oa ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = oa.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = oa.journal, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(modifier = Modifier.height(16.dp))

                        if (oa.isOa && oa.pdfUrl != null) {
                            // SUCCESS: Free PDF found!
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF2E7D32))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open Access Available!", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { uriHandler.openUri(oa.pdfUrl) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Read PDF Now")
                            }
                        } else {
                            // FAILED: Behind a Paywall
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = "Paywall", tint = Color(0xFFC62828))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Behind a Paywall", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No free version of this article could be found in public repositories.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}