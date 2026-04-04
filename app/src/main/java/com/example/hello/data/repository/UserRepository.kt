package com.example.hello.data.repository

import com.example.hello.data.api.CommonRequest
import com.example.hello.data.api.UserApi
import com.example.hello.data.model.Result
import com.example.hello.data.model.dto.UserInfoDto
import com.example.hello.data.model.dto.UserVerifyRequest
import com.example.hello.data.model.dto.VerifyCodeRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userApi: UserApi,
    private val tokenManager: TokenManager
) {
    
    suspend fun getUserInfo(): Result<UserInfoDto> {
        return CommonRequest.safeApiCallWithResponse { userApi.getUserInfo() }
    }
    
    suspend fun sendVerifyCode(studentId: String): Result<Unit> {
        val email = "$studentId@buaa.edu.cn"
        return CommonRequest.safeApiCallWithResponse(
            apiCall = { userApi.sendVerifyCode(VerifyCodeRequest(email, 4)) },
            transform = { Unit }
        )
    }
    
    suspend fun verifyUser(studentId: String, verifyCode: String): Result<Unit> {
        val email = "$studentId@buaa.edu.cn"
        return CommonRequest.safeApiCallWithResponse(
            apiCall = { userApi.verifyUser(UserVerifyRequest(email, verifyCode)) },
            transform = { Unit }
        )
    }
}
