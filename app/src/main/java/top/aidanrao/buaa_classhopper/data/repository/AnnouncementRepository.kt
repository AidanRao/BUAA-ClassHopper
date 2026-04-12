package top.aidanrao.buaa_classhopper.data.repository

import top.aidanrao.buaa_classhopper.data.api.AnnouncementApi
import top.aidanrao.buaa_classhopper.data.api.CommonRequest
import top.aidanrao.buaa_classhopper.data.model.Result
import top.aidanrao.buaa_classhopper.data.model.dto.AnnouncementDetailDto
import top.aidanrao.buaa_classhopper.data.model.dto.AnnouncementDto
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
