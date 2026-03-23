package com.example.scholium.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// The overall chat session
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val title: String,
    val pdfPath: String?,
    val timestamp: Long = System.currentTimeMillis()
)

//The individual text bubbles
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val messageId: Long = 0,
    val sessionId: Long,
    val role: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)