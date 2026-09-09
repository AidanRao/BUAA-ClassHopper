package top.aidanrao.buaa_classhopper.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import top.aidanrao.buaa_classhopper.data.model.dto.IclassLoginResponse

interface IclassAuthApi {
    @Streaming
    @GET("./")
    suspend fun jumpMyCenter(@Query("type") type: String = "jumpMyCenter"): Response<ResponseBody>

    @GET("eschool/app/user/login_buaa.do")
    suspend fun login(
        @Query("phone") phone: String,
        @Query("password") password: String = "",
        @Query("verificationType") verificationType: String = "2",
        @Query("verificationUrl") verificationUrl: String = "",
        @Query("userLevel") userLevel: String = "1"
    ): IclassLoginResponse
}
