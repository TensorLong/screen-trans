package com.galaxy.airviewdictionary.data.remote.translation.yandex

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface YandexService {

    @Headers("Content-Type: application/json")
    @POST("translate/v2/translate")
    suspend fun send(
        @Header("Authorization") bearerToken: String,
        @Body body: RequestBody
    ): ResponseBody
}
