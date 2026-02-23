package com.example.hello.data.api

import com.example.hello.data.model.ApiResponse
import com.example.hello.data.model.dto.FallbackScheduleResponse
import com.example.hello.data.model.dto.IclassLoginResponse
import com.example.hello.data.model.dto.IclassScheduleResponse
import com.example.hello.data.model.dto.IclassSignResponse
import com.example.hello.data.model.dto.QRCodeInfoDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface IclassApi {
    
    @GET("app/user/login.action")
    suspend fun login(
        @Query("password") password: String = "",
        @Query("phone") phone: String,
        @Query("userLevel") userLevel: String = "1",
        @Query("verificationType") verificationType: String = "2",
        @Query("verificationUrl") verificationUrl: String = ""
    ): IclassLoginResponse
    
    @GET("app/course/get_stu_course_sched.action")
    suspend fun getCourseSchedule(
        @Query("dateStr") dateStr: String,
        @Query("id") userId: String,
        @Header("sessionId") sessionId: String
    ): IclassScheduleResponse
    
    @FormUrlEncoded
    @POST("app/course/stu_scan_sign.action")
    suspend fun signClass(
        @Query("courseSchedId") courseSchedId: Int,
        @Query("timestamp") timestamp: Long,
        @Field("id") userId: String
    ): IclassSignResponse
}

interface FallbackApi {
    
    @GET("api/course/list")
    suspend fun getCourseSchedule(
        @Query("date") date: String,
        @Header("Authorization") token: String
    ): FallbackScheduleResponse
    
    @POST("api/course/sign")
    suspend fun signClass(
        @Query("courseScheduleId") courseScheduleId: Int,
        @Header("Authorization") token: String
    ): retrofit2.Response<Unit>
}

interface QRCodeApi {
    
    @GET("user/auth/qrcode")
    suspend fun getQRCodeInfo(@Query("scanId") scanId: String): ApiResponse<QRCodeInfoDto>
    
    @POST("user/auth/qrcode/confirm")
    suspend fun confirmQRCodeLogin(@Query("scanId") scanId: String): retrofit2.Response<Unit>
}
