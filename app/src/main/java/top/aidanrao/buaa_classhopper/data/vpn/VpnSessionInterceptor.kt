package top.aidanrao.buaa_classhopper.data.vpn

import okhttp3.Interceptor
import okhttp3.Response

/** The jump page is HTML by design. Business APIs must still return JSON. */
class VpnSessionInterceptor(
    private val vpn: Boolean,
    private val base: okhttp3.HttpUrl = IclassSession.baseUrl(vpn),
    private val onExpired: () -> Unit
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val jump = IclassSession.isLanding(request.url, base) &&
            request.url.queryParameter("type") == "jumpMyCenter"
        val validLanding = IclassSession.isLanding(response.request.url, base) &&
            IclassSession.loginName(response.request.url) != null
        val html = response.header("Content-Type").orEmpty().contains("text/html", true)
        if (response.code in listOf(401, 403) || (jump && response.isSuccessful && !validLanding) || (!jump && html && response.isSuccessful)) {
            response.close()
            onExpired()
            val final = response.request.url
            throw IclassSessionExpiredException(vpn,
                "stage=${if (jump) "jumpMyCenter" else "api"}, status=${response.code}, " +
                    "host=${final.host}, port=${final.port}, path=${final.encodedPath}, " +
                    "html=$html, validLanding=$validLanding")
        }
        return response
    }
}
