package top.aidanrao.buaa_classhopper.data.api.interceptor

import top.aidanrao.buaa_classhopper.data.repository.TokenManager
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
