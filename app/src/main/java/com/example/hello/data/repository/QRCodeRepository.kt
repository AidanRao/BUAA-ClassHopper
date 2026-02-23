package com.example.hello.data.repository

import com.example.hello.data.api.CommonRequest
import com.example.hello.data.api.QRCodeApi
import com.example.hello.data.model.Result
import com.example.hello.data.model.dto.QRCodeInfoDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QRCodeRepository @Inject constructor(
    private val qrCodeApi: QRCodeApi,
    private val tokenManager: TokenManager
) {
    
    suspend fun getQRCodeInfo(scanId: String): Result<QRCodeInfoDto> {
        return CommonRequest.safeApiCallWithResponse { qrCodeApi.getQRCodeInfo(scanId) }
    }
    
    suspend fun confirmQRCodeLogin(scanId: String): Result<Unit> {
        return CommonRequest.safeApiCall { qrCodeApi.confirmQRCodeLogin(scanId) }
    }
}
