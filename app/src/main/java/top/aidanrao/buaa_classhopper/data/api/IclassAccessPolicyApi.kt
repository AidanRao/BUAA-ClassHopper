package top.aidanrao.buaa_classhopper.data.api

import retrofit2.Response
import retrofit2.http.GET
import top.aidanrao.buaa_classhopper.data.model.ApiResponse
import top.aidanrao.buaa_classhopper.data.model.dto.IclassAccessPolicyDto

interface IclassAccessPolicyApi {
    @GET("v1/iclass/access-policy")
    suspend fun getAccessPolicy(): Response<ApiResponse<IclassAccessPolicyDto>>
}
