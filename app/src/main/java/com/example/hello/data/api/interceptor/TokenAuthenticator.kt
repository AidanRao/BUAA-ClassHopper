package com.example.hello.data.api.interceptor

import android.util.Log
import com.example.hello.data.repository.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager
) : Authenticator {
    
    companion object {
        private const val TAG = "TokenAuthenticator"
        private const val HEADER_AUTHORIZATION = "Authorization"
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != 401) {
            return null
        }
        
        Log.w(TAG, "Received 401, attempting to refresh token")
        
        tokenManager.invalidateToken()
        
        return runBlocking {
            val newToken = tokenManager.refreshToken()
            
            if (newToken != null) {
                Log.i(TAG, "Token refreshed successfully, retrying request")
                response.request.newBuilder()
                    .header(HEADER_AUTHORIZATION, newToken)
                    .build()
            } else {
                Log.e(TAG, "Failed to refresh token")
                null
            }
        }
    }
}
