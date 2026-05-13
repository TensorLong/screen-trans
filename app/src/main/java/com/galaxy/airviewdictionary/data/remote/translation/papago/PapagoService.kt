package com.galaxy.airviewdictionary.data.remote.translation.papago

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface PapagoService {

    @Headers("Content-Type: application/x-www-form-urlencoded")
    @POST("nmt/v1/translation")
    suspend fun send(
        @Header("X-NCP-APIGW-API-KEY-ID") clientId: String,
        @Header("X-NCP-APIGW-API-KEY") clientSecret: String,
        @Body body: RequestBody
    ): ResponseBody
}