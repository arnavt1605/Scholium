package com.example.scholium.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scholium Tools", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. MAIN FEATURE: Analyze Paper (Routes to your hard work!)
            FeatureCard(
                title = "Analyze Research Paper",
                description = "Upload PDF, Extract Text, Ask AI",
                icon = Icons.Default.Analytics,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = { navController.navigate("analyze_paper") }
            )

            Text("Quick Tools", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

            // 2. Related Papers (Placeholder route)
            FeatureCard(
                title = "Find Related Papers",
                description = "Get a list of similar academic papers",
                icon = Icons.AutoMirrored.Filled.Article,
                onClick = { /* Link */ }
            )

            // 3. Citation Formatter
            FeatureCard(
                title = "Citation Formatter",
                description = "Convert DOI to APA/MLA citations",
                icon = Icons.Default.Book,
                onClick = { /* Link */ }
            )

            // 4. OA Checker
            FeatureCard(
                title = "Open Access Checker",
                description = "Check if a paper is paywalled",
                icon = Icons.Default.LockOpen,
                onClick = { /* Link */ }
            )

            // 5. Find OA PDF
            FeatureCard(
                title = "Find PDF Link",
                description = "Get direct download link for papers",
                icon = Icons.Default.Search,
                onClick = { /* Link */ }
            )
        }
    }
}

@Composable
fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}