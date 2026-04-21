package top.aidanrao.buaa_classhopper.data.api

import top.aidanrao.buaa_classhopper.data.model.ApiResponse
import top.aidanrao.buaa_classhopper.data.model.dto.AuthRequest
import top.aidanrao.buaa_classhopper.data.model.dto.AuthResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    
    @POST("user/third-auth")
    suspend fun authenticate(@Body request: AuthRequest): ApiResponse<AuthResponse>
}
