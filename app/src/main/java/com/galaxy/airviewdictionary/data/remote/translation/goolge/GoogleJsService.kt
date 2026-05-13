package com.galaxy.airviewdictionary.data.remote.translation.goolge

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query


interface GoogleWebService {
    @Headers(
        "accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8",
        "accept-encoding: gzip, deflate, br",
        "connection: Keep-Alive",
        "content-type: application/json",
    )
    @GET("translate_a/single")
    suspend fun send(
        @Query("client") client: String = "webapp",
        @Query("dt") dt: String = "t",
        @Query("ie") ie: String = "UTF-8",
        @Query("oe") oe: String = "UTF-8",
        @Query("source") source: String = "btn",
        @Query("ssel") ssel: String = "0",
        @Query("tsel") tsel: String = "0",
        @Query("kc") kc: String = "1",
        @Query("sl") sl: String,
        @Query("tl") tl: String,
        @Query("hl") hl: String,
        @Query("tk") tk: String,
        @Query("q") sourceText: String
    ): ResponseBody
}
