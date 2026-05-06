package com.example.hebrew.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

interface OpenAIService {

    @POST("v1/chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): ChatResponse

    @Streaming
    @POST("v1/chat/completions")
    suspend fun getCompletionStream(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): Response<ResponseBody>
}
