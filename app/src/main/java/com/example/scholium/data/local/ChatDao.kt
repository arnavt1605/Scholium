package com.example.scholium.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ChatDao {
    // Creates a new session and returns its new ID
    @Insert
    suspend fun insertSession(session: ChatSessionEntity): Long

    // Saves a single message bubble
    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)


    // Gets all chat sessions (for your history menu later)
    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    suspend fun getAllSessions(): List<ChatSessionEntity>

    // Gets all the text bubbles for one specific chat, in order
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessageEntity>

    //Get a single session by its ID
    @Query("SELECT * FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: Long): ChatSessionEntity?

}