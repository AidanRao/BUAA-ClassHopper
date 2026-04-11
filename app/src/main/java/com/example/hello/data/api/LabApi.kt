package com.example.hello.data.api

import com.example.hello.data.model.ApiResponse
import retrofit2.http.GET

interface LabApi {

    @GET("message/feature")
    suspend fun getAvailableFeatures(): ApiResponse<List<String>>
}
