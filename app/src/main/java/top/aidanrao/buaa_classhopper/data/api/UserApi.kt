package top.aidanrao.buaa_classhopper.data.api

import top.aidanrao.buaa_classhopper.data.model.ApiResponse
import top.aidanrao.buaa_classhopper.data.model.dto.UserInfoDto
import top.aidanrao.buaa_classhopper.data.model.dto.UserVerifyRequest
import top.aidanrao.buaa_classhopper.data.model.dto.VerifyCodeRequest
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
