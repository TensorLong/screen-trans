package com.galaxy.airviewdictionary.data.remote.translation.azure

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface AzureService {

    @Headers("Content-Type: application/json")
    @POST("translate?api-version=3.0")
    suspend fun send(
        @Header("Ocp-Apim-Subscription-Key") translatorKey: String,
        @Header("Ocp-Apim-Subscription-Region") resourceLocation: String,
        @Query("from") from: String? = null,
        @Query("to") to: String,
        @Body body: RequestBody,
    ): ResponseBody
}
