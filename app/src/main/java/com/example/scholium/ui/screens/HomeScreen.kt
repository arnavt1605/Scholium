package com.example.scholium.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.FormatQuote
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Functions

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
            // 1. Analyze Paper
            FeatureCard(
                title = "Analyze Research Paper",
                description = "Upload PDF, Extract Text, Ask AI",
                icon = Icons.Default.Analytics,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = { navController.navigate("analyze_paper") }
            )

            //2. Chat history
            FeatureCard(
                title = "Analysis History",
                description = "View your past AI explanations",
                icon = Icons.Default.History,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { navController.navigate("history") }
            )

            Text("Quick Tools", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

            // 3. Citation Formatter
            FeatureCard(
                title = "Citation Generator",
                description = "Convert any DOI into APA, IEEE, or MLA instantly.",
                icon = Icons.Default.FormatQuote,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { navController.navigate("citation_generator") }
            )

            // 4. Find related papers
            FeatureCard(
                title = "Find Related Papers",
                description = "Discover literature based on a topic or title.",
                icon = Icons.Default.Search, // Ensure androidx.compose.material.icons.filled.Search is imported
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { navController.navigate("related_papers") }
            )

            // 5. Open Access Checker
            FeatureCard(
                title = "Open Access Finder",
                description = "Bypass paywalls by finding legal, free PDF versions.",
                icon = Icons.Default.LockOpen, // Ensure androidx.compose.material.icons.filled.LockOpen is imported
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = { navController.navigate("open_access") }
            )

            // 6. Abstract TLDR
            FeatureCard(
                title = "Abstract TLDR",
                description = "Paste a dense abstract and let AI extract the core findings.",
                icon = Icons.Default.AutoAwesome, // Ensure androidx.compose.material.icons.filled.AutoAwesome is imported
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { navController.navigate("abstract_summary") }
            )

            //7. AI Paper reviewer
            FeatureCard(
                title = "AI Paper Reviewer",
                description = "Upload a draft. Gemma 3 Vision acts as Reviewer 2 to critique your work.",
                icon = Icons.Default.RateReview,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = { navController.navigate("paper_reviewer") }
            )

            //8. Latex Equation
            FeatureCard(
                title = "LaTeX Generator",
                description = "Snap a photo of an equation and instantly convert it to LaTeX code.",
                icon = Icons.Default.Functions,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { navController.navigate("latex_generator") }
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