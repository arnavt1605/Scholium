package com.example.scholium.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

// Data class to hold the parsed API results
data class AcademicPaper(
    val title: String,
    val authors: String,
    val year: String,
    val abstract: String,
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatedPapersScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var papersList by remember { mutableStateOf<List<AcademicPaper>>(emptyList()) }

    fun searchPapers(query: String) {
        if (query.isBlank()) return
        isLoading = true
        errorMessage = ""
        papersList = emptyList()

        scope.launch(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")

                val url = "https://api.crossref.org/works?query=$encodedQuery&select=title,author,issued,abstract,URL&rows=5"

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "ScholiumApp/1.0 (mailto:developer@scholium.app)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        val jsonObject = JSONObject(responseBody)

                        // Crossref wraps their results in a "message" object, then an "items" array
                        val messageObj = jsonObject.optJSONObject("message")
                        val itemsArray = messageObj?.optJSONArray("items")

                        val parsedPapers = mutableListOf<AcademicPaper>()

                        if (itemsArray != null) {
                            for (i in 0 until itemsArray.length()) {
                                val paperObj = itemsArray.getJSONObject(i)

                                // 1. Extract Title (Crossref returns titles as an array)
                                val titleArray = paperObj.optJSONArray("title")
                                val title = if (titleArray != null && titleArray.length() > 0) titleArray.getString(0) else "Untitled Paper"

                                // 2. Extract Authors
                                val authorsArray = paperObj.optJSONArray("author")
                                val authorNames = mutableListOf<String>()
                                if (authorsArray != null) {
                                    for (j in 0 until authorsArray.length()) {
                                        val authorObj = authorsArray.getJSONObject(j)
                                        val given = authorObj.optString("given", "")
                                        val family = authorObj.optString("family", "")
                                        authorNames.add("$given $family".trim())
                                    }
                                }
                                val authorsString = if (authorNames.isNotEmpty()) authorNames.joinToString(", ") else "Unknown Authors"

                                // 3. Extract Year
                                val issuedObj = paperObj.optJSONObject("issued")
                                val datePartsArray = issuedObj?.optJSONArray("date-parts")
                                val yearArray = datePartsArray?.optJSONArray(0)
                                val year = if (yearArray != null && yearArray.length() > 0) yearArray.optInt(0).toString() else "N/A"

                                // 4. Clean the Abstract (Crossref leaves <jats:p> HTML tags in their text)
                                val rawAbstract = paperObj.optString("abstract", "No abstract available.")
                                val cleanAbstract = rawAbstract.replace(Regex("<.*?>"), "").trim()

                                // 5. Add to list
                                parsedPapers.add(
                                    AcademicPaper(
                                        title = title,
                                        authors = authorsString,
                                        year = year,
                                        abstract = cleanAbstract,
                                        url = paperObj.optString("URL", "")
                                    )
                                )
                            }
                        }

                        launch(Dispatchers.Main) {
                            if (parsedPapers.isEmpty()) {
                                errorMessage = "No related papers found for this topic."
                            } else {
                                papersList = parsedPapers
                            }
                        }
                    } else {
                        val code = response.code
                        launch(Dispatchers.Main) { errorMessage = "API Error $code: Could not fetch results." }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) { errorMessage = "Network Error: Check your internet connection." }
            } finally {
                launch(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Related Papers", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
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
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Enter a topic, keyword, or paper title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { searchPapers(searchQuery) }, enabled = !isLoading && searchQuery.isNotBlank()) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(papersList) { paper ->
                    PaperResultCard(paper = paper, onOpenUrl = {
                        if (paper.url.isNotEmpty()) {
                            uriHandler.openUri(paper.url)
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun PaperResultCard(paper: AcademicPaper, onOpenUrl: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = paper.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            Text(text = "${paper.year} • ${paper.authors}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = paper.abstract, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (paper.url.isNotEmpty()) {
                TextButton(
                    onClick = onOpenUrl,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Read Source")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Link", modifier = Modifier.size(16.dp))
                }
            } else {
                Text("Tap card to read abstract", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}