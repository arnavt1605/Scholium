package com.example.scholium.data

import com.example.scholium.domain.ChatMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SarvamApiService {

    private const val API_KEY = "sk_59k2cw5q_rSbUWFbJ4OeexGxuE4g4IX4Z"

    private const val URL = "https://api.sarvam.ai/v1/chat/completions"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun getChatResponse(fullChatHistory: List<ChatMessage>): String {
        return try {

            val messagesToSend = mutableListOf<ChatMessage>()

            // Always include system message if present
            if (fullChatHistory.isNotEmpty()) {
                messagesToSend.add(fullChatHistory.first())
            }

            // Last 4 messages only
            val recentMessages = fullChatHistory.drop(1).takeLast(4)
            messagesToSend.addAll(recentMessages)

            // Build messages array
            val messagesArray = JSONArray()
            for (msg in messagesToSend) {
                val messageObj = JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                }
                messagesArray.put(messageObj)
            }

            // Build request body
            val jsonBody = JSONObject().apply {
                put("model", "sarvam-m") // ✅ safer default model
                put("messages", messagesArray)
                put("temperature", 0.3)
                put("max_tokens", 512) // ✅ prevents schema errors
            }.toString()

            val request = Request.Builder()
                .url(URL)
                .addHeader("Authorization", "Bearer $API_KEY") // ✅ FIXED
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->

                val responseBody = response.body?.string()

                // 🔴 CRITICAL DEBUG FIX
                if (!response.isSuccessful) {
                    return "Error ${response.code}: $responseBody"
                }

                if (responseBody.isNullOrEmpty()) {
                    return "Error: Empty response"
                }

                val jsonResponse = JSONObject(responseBody)

                val choices = jsonResponse.optJSONArray("choices")
                    ?: return "Error: No choices in response"

                if (choices.length() == 0) {
                    return "Error: Empty choices array"
                }

                val message = choices.getJSONObject(0)
                    .optJSONObject("message")
                    ?: return "Error: No message in response"

                val rawText = message.optString("content", "")

                // Remove <think> tags if present
                val cleanText = rawText.replace(
                    Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL),
                    ""
                ).trim()

                return cleanText.ifEmpty { "No response generated." }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            "Exception: ${e.message}"
        }
    }
}