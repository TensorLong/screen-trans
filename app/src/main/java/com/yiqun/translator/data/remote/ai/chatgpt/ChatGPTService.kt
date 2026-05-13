package com.yiqun.translator.data.remote.ai.chatgpt

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

interface ChatGPTService {

    @Headers("Content-Type: application/json")
    @POST
    suspend fun send(
        @Url url: String,
        @Header("Authorization") apiKey: String,
        @Body body: RequestBody
    ): ChatGPTResponse

    @GET
    suspend fun models(
        @Url url: String,
        @Header("Authorization") apiKey: String,
    ): ModelListResponse
}

data class ChatGPTResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)

data class Message(
    val role: String,
    val content: String
)

data class ModelListResponse(
    val data: List<ModelInfo> = emptyList(),
)

data class ModelInfo(
    val id: String = "",
)


