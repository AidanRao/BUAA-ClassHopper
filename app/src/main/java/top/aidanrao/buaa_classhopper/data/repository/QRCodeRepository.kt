package top.aidanrao.buaa_classhopper.data.repository

import top.aidanrao.buaa_classhopper.data.api.CommonRequest
import top.aidanrao.buaa_classhopper.data.api.QRCodeApi
import top.aidanrao.buaa_classhopper.data.model.Result
import top.aidanrao.buaa_classhopper.data.model.dto.QRCodeInfoDto
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
