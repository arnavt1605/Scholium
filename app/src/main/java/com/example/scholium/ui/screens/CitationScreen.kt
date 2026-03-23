package com.example.scholium.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitationScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var doiInput by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf("apa") }
    var generatedCitation by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val citationStyles = listOf("apa" to "APA", "ieee" to "IEEE", "mla" to "MLA")

    // The Network Call Function
    fun fetchCitation(doi: String, style: String) {
        if (doi.isBlank()) return
        isLoading = true
        errorMessage = ""
        generatedCitation = ""

        scope.launch(Dispatchers.IO) {
            try {
                // Clean the input in case they pasted the full URL instead of just the DOI
                val cleanDoi = doi.replace("https://doi.org/", "").replace("http://dx.doi.org/", "").trim()

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://doi.org/$cleanDoi")
                    // This hidden header tells the server to return text, not a webpage!
                    .header("Accept", "text/x-bibliography; style=$style")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val result = response.body?.string() ?: "Empty response"
                        launch(Dispatchers.Main) { generatedCitation = result.trim() }
                    } else {
                        launch(Dispatchers.Main) { errorMessage = "Could not find DOI. Please check the format." }
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { errorMessage = "Network error: ${e.message}" }
            } finally {
                launch(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Citation Generator", color = MaterialTheme.colorScheme.onPrimary) },
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
            Text("Enter a DOI to generate an instant, perfectly formatted citation.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = doiInput,
                onValueChange = { doiInput = it },
                label = { Text("DOI (e.g., 10.1038/s41586-020-2649-2)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Style Selector
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                citationStyles.forEach { (id, name) ->
                    FilterChip(
                        selected = selectedStyle == id,
                        onClick = { selectedStyle = id },
                        label = { Text(name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { fetchCitation(doiInput, selectedStyle) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && doiInput.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Generate Citation")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Error Message
            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }

            // Success Result
            if (generatedCitation.isNotEmpty()) {
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
                            Text("Formatted Citation ($selectedStyle)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(generatedCitation))
                            }) {
                                Icon(Icons.Default.ContentCopy, "Copy to Clipboard", modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // SelectionContainer allows the user to highlight and copy text manually
                        SelectionContainer {
                            Text(generatedCitation, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}