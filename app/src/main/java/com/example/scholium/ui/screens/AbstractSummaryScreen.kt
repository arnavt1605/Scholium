package com.example.scholium.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.scholium.data.SarvamApiService
import com.example.scholium.domain.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbstractSummaryScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var abstractInput by remember { mutableStateOf("") }
    var summaryResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    fun generateSummary(text: String) {
        if (text.isBlank()) return
        isLoading = true
        summaryResult = ""

        scope.launch(Dispatchers.IO) {
            try {
                val systemPrompt = """
                    You are an expert research assistant. Read the following academic abstract and summarize it into exactly three distinct bullet points:
                    1. The Problem (What are they trying to solve?)
                    2. The Method (How did they do it?)
                    3. The Result (What did they find?)
                    Do not include any introductory or concluding text. Be concise and use simple language.
                    Do not wrap text in any sort of characters like -, *. Just send plain text.
                """.trimIndent()


                val payload = listOf(
                    ChatMessage(role = "system", content = systemPrompt, isUser = false),
                    ChatMessage(role = "user", content = text, isUser = true)
                )

                val aiResponse = SarvamApiService.getChatResponse(payload)

                launch(Dispatchers.Main) {
                    summaryResult = aiResponse
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    summaryResult = "Error: Could not generate summary. Check your connection or API key."
                }
            } finally {
                launch(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Abstract TL;DR", color = MaterialTheme.colorScheme.onPrimary) },
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
                text = "Paste a dense abstract below, and AI will extract the Problem, Method, and Result instantly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = abstractInput,
                onValueChange = { abstractInput = it },
                label = { Text("Paste Abstract Here") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 300.dp),
                maxLines = 15
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { generateSummary(abstractInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && abstractInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Summarize with AI")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AI Result Card
            if (summaryResult.isNotEmpty()) {
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
                            Text("TL;DR Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(summaryResult))
                            }) {
                                Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(20.dp))
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // SelectionContainer allows user to highlight text
                        SelectionContainer {
                            Text(text = summaryResult, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}