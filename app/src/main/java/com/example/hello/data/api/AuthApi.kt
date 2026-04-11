package com.example.hello.data.api

import com.example.hello.data.model.ApiResponse
import com.example.hello.data.model.dto.AuthRequest
import com.example.hello.data.model.dto.AuthResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    
    @POST("user/third-auth")
    suspend fun authenticate(@Body request: AuthRequest): ApiResponse<AuthResponse>
}
