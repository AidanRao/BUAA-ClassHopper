package top.aidanrao.buaa_classhopper.data.repository

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val tokenManager: TokenManager
) {
    suspend fun getValidToken(): String? {
        return tokenManager.getValidToken()
    }
    
    suspend fun refreshToken(): String? {
        return tokenManager.refreshToken()
    }
    
    fun invalidateToken() {
        tokenManager.invalidateToken()
    }
}
