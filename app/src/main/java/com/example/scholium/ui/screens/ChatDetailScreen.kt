package com.example.scholium.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.scholium.data.local.ChatDao
import com.example.scholium.data.local.ChatMessageEntity
import com.example.scholium.ui.components.ChatBubble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(navController: NavController, chatDao: ChatDao, sessionId: Long) {
    var messages by remember { mutableStateOf<List<ChatMessageEntity>>(emptyList()) }

    // Fetch messages for this specific session
    LaunchedEffect(sessionId) {
        val pastMessages = withContext(Dispatchers.IO) {
            chatDao.getMessagesForSession(sessionId)
        }
        messages = pastMessages
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis Record", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    navController.navigate("analyze_paper?sessionId=$sessionId")
                },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Continue") },
                text = { Text("Continue Chat") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filter out the hidden "system" prompt so it looks like a clean chat
            items(messages.filter { it.role != "system" }) { msg ->
                // We adapt the Database Entity to the Domain format our ChatBubble expects
                val domainMsg = com.example.scholium.domain.ChatMessage(
                    role = msg.role,
                    content = msg.content,
                    isUser = msg.isUser
                )
                ChatBubble(message = domainMsg)
            }
        }
    }
}
