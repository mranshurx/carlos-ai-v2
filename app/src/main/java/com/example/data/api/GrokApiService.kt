package com.example.data.api

import com.example.data.model.GrokRequest
import com.example.data.model.GrokResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface GrokApiService {

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: GrokRequest
    ): Response<GrokResponse>
}

object GrokApiClient {

    private var currentBaseUrl: String = ""
    private var currentRetrofit: Retrofit? = null

    fun getService(baseUrl: String): GrokApiService {
        val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (currentRetrofit == null || currentBaseUrl != sanitizedUrl) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            currentRetrofit = Retrofit.Builder()
                .baseUrl(sanitizedUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
            currentBaseUrl = sanitizedUrl
        }
        return currentRetrofit!!.create(GrokApiService::class.java)
    }
}
