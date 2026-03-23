package com.example.scholium.domain

data class ChatMessage(
    val role: String,    // "system", "user", or "assistant"
    val content: String, // The actual text
    val isUser: Boolean  // Helper for the UI to align bubbles left/right
)