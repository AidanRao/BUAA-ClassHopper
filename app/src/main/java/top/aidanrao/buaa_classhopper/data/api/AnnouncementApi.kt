package top.aidanrao.buaa_classhopper.data.api

import top.aidanrao.buaa_classhopper.data.model.ApiResponse
import top.aidanrao.buaa_classhopper.data.model.dto.AnnouncementDetailDto
import top.aidanrao.buaa_classhopper.data.model.dto.AnnouncementDto
import retrofit2.http.GET
import retrofit2.http.Path

interface AnnouncementApi {
    
    @GET("message/announcement/list")
    suspend fun getAnnouncements(): ApiResponse<List<AnnouncementDto>>
    
    @GET("message/announcement/{id}")
    suspend fun getAnnouncementDetail(@Path("id") id: String): ApiResponse<AnnouncementDetailDto>
}
