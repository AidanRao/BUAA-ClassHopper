package com.example.hello.data.api

import com.example.hello.data.model.ApiResponse
import com.example.hello.data.model.dto.AnnouncementDetailDto
import com.example.hello.data.model.dto.AnnouncementDto
import retrofit2.http.GET
import retrofit2.http.Path

interface AnnouncementApi {
    
    @GET("message/announcement/list")
    suspend fun getAnnouncements(): ApiResponse<List<AnnouncementDto>>
    
    @GET("message/announcement/{id}")
    suspend fun getAnnouncementDetail(@Path("id") id: String): ApiResponse<AnnouncementDetailDto>
}
