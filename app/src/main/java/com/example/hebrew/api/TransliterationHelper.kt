package com.example.hebrew.api

object TransliterationHelper {
    suspend fun transliterate(apiKey: String, hebrewText: String): String {
        val response = OpenAIClient.service.getCompletion(
            auth = "Bearer $apiKey",
            request = ChatRequest(
                messages = listOf(ChatMessage("user",
                    "Транслитерируй следующий текст с иврита русскими буквами (фонетическая транслитерация). Только результат, без объяснений:\n$hebrewText"
                )),
                max_tokens = 150
            )
        )
        return response.choices.firstOrNull()?.message?.content?.trim() ?: ""
    }
}
