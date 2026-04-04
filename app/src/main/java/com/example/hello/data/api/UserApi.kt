package com.example.hello.data.api

import com.example.hello.data.model.ApiResponse
import com.example.hello.data.model.dto.UserInfoDto
import com.example.hello.data.model.dto.UserVerifyRequest
import com.example.hello.data.model.dto.VerifyCodeRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface UserApi {
    
    @GET("user/info")
    suspend fun getUserInfo(): ApiResponse<UserInfoDto>
    
    @POST("verify")
    suspend fun sendVerifyCode(@Body request: VerifyCodeRequest): ApiResponse<Any>
    
    @POST("user/verify")
    suspend fun verifyUser(@Body request: UserVerifyRequest): ApiResponse<Any>
}
