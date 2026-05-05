package com.example.hebrew.api

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<ChatMessage>,
    val max_tokens: Int = 800,
    val temperature: Double = 0.3
)

data class ChatChoice(
    val message: ChatMessage,
    val finish_reason: String?
)

data class ChatResponse(
    val choices: List<ChatChoice>
)
