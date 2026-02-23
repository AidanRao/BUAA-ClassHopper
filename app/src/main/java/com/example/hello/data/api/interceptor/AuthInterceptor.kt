package com.example.hello.data.api.interceptor

import android.util.Log
import com.example.hello.data.repository.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {
    
    companion object {
        private const val TAG = "AuthInterceptor"
        private const val HEADER_AUTHORIZATION = "Authorization"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()
        
        val token = tokenManager.getCachedToken()
        
        val newRequest = if (token != null && shouldAddAuthHeader(url)) {
            originalRequest.newBuilder()
                .addHeader(HEADER_AUTHORIZATION, token)
                .build()
        } else {
            originalRequest
        }
        
        return chain.proceed(newRequest)
    }

    private fun shouldAddAuthHeader(url: String): Boolean {
        val noAuthEndpoints = listOf(
            "user/third-auth",
            "user/auth/qrcode"
        )
        return noAuthEndpoints.none { url.contains(it) }
    }
}
