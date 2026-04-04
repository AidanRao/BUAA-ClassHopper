package com.example.hello.data.repository

import com.example.hello.data.api.AnnouncementApi
import com.example.hello.data.api.CommonRequest
import com.example.hello.data.model.Result
import com.example.hello.data.model.dto.AnnouncementDetailDto
import com.example.hello.data.model.dto.AnnouncementDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnouncementRepository @Inject constructor(
    private val announcementApi: AnnouncementApi,
    private val tokenManager: TokenManager
) {
    
    suspend fun getAnnouncements(): Result<List<AnnouncementDto>> {
        return CommonRequest.safeApiCallWithResponse { announcementApi.getAnnouncements() }
    }
    
    suspend fun getAnnouncementDetail(id: String): Result<AnnouncementDetailDto> {
        return CommonRequest.safeApiCallWithResponse { announcementApi.getAnnouncementDetail(id) }
    }
}
