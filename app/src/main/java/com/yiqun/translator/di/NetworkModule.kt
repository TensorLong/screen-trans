package com.yiqun.translator.di

import com.yiqun.translator.data.remote.ai.chatgpt.ChatGPTKit
import com.yiqun.translator.data.remote.translation.azure.AzureKit
import com.yiqun.translator.data.remote.translation.goolge.GoogleWebKit
import com.yiqun.translator.data.remote.translation.papago.PapagoKit
import com.yiqun.translator.data.remote.translation.yandex.YandexKit
import com.yiqun.translator.data.remote.ai.chatgpt.ChatGPTService
import com.yiqun.translator.data.remote.translation.azure.AzureService
import com.yiqun.translator.data.remote.translation.goolge.GoogleWebService
import com.yiqun.translator.data.remote.translation.papago.PapagoService
import com.yiqun.translator.data.remote.translation.yandex.YandexService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton


@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GoogleWebRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AzureRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class YandexRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PapagoRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChatGPTRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient().newBuilder()
            .addInterceptor(ResponseInterceptor())
            .readTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @GoogleWebRetrofit
    @Provides
    @Singleton
    fun provideGoogleWebRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(GoogleWebKit.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @GoogleWebRetrofit
    @Provides
    @Singleton
    fun provideGoogleWebService(@GoogleWebRetrofit retrofit: Retrofit): GoogleWebService {
        return retrofit.create(GoogleWebService::class.java)
    }

    @AzureRetrofit
    @Provides
    @Singleton
    fun provideAzureRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(AzureKit.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @AzureRetrofit
    @Provides
    @Singleton
    fun provideAzureService(@AzureRetrofit retrofit: Retrofit): AzureService {
        return retrofit.create(AzureService::class.java)
    }

    @YandexRetrofit
    @Provides
    @Singleton
    fun provideYandexRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(YandexKit.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @YandexRetrofit
    @Provides
    @Singleton
    fun provideYandexService(@YandexRetrofit retrofit: Retrofit): YandexService {
        return retrofit.create(YandexService::class.java)
    }

    @PapagoRetrofit
    @Provides
    @Singleton
    fun providePapagoRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(PapagoKit.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @PapagoRetrofit
    @Provides
    @Singleton
    fun providePapagoService(@PapagoRetrofit retrofit: Retrofit): PapagoService {
        return retrofit.create(PapagoService::class.java)
    }

    @ChatGPTRetrofit
    @Provides
    @Singleton
    fun provideChatGPTRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(ChatGPTKit.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @ChatGPTRetrofit
    @Provides
    @Singleton
    fun provideChatGPTService(@ChatGPTRetrofit retrofit: Retrofit): ChatGPTService {
        return retrofit.create(ChatGPTService::class.java)
    }
}

class ResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        Timber.tag("response.code").d("response.code %s", response.code)
        return response
    }
}