package top.aidanrao.buaa_classhopper.data.api

import top.aidanrao.buaa_classhopper.data.model.ApiResponse
import retrofit2.http.GET

interface LabApi {

    @GET("message/feature")
    suspend fun getAvailableFeatures(): ApiResponse<List<String>>
}
