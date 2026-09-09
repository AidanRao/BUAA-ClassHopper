package top.aidanrao.buaa_classhopper.data.vpn

import java.io.IOException
import java.net.URLDecoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import top.aidanrao.buaa_classhopper.data.api.IclassAuthApi
import top.aidanrao.buaa_classhopper.data.model.dto.IclassLoginResponse

open class IclassSessionExpiredException(val vpn: Boolean) : IOException(messageFor(vpn)) {
    companion object {
        const val DIRECT_MESSAGE = "直连 SSO 未登录或会话已失效，请到设置中重新登录"
        const val VPN_MESSAGE = "VPN SSO 未登录或会话已失效，请到设置中重新登录"
        fun messageFor(vpn: Boolean) = if (vpn) VPN_MESSAGE else DIRECT_MESSAGE
    }
}

object IclassSession {
    fun baseUrl(vpn: Boolean): HttpUrl =
        (if (vpn) VpnEndpoints.ICLASS_VPN_8346 else VpnEndpoints.ICLASS_DIRECT_8346).toHttpUrl()

    fun jumpUrl(vpn: Boolean): String = baseUrl(vpn).newBuilder()
        .addQueryParameter("type", "jumpMyCenter").build().toString()

    fun isLanding(url: HttpUrl, base: HttpUrl): Boolean =
        url.scheme == base.scheme && url.host == base.host && url.port == base.port &&
            url.encodedPath == base.encodedPath

    fun isVpnPortal(url: HttpUrl): Boolean {
        if (url.host != VpnEndpoints.VPN_HOST) return false
        val path = url.encodedPath.lowercase()
        return !path.startsWith("/http") &&
            listOf("/login", "/authserver", "/cas", "/logout").none { path.startsWith(it) }
    }

    // URL query syntax allows literal '+'. Decode percent escapes exactly once, not as form data.
    fun loginName(url: HttpUrl): String? = url.encodedQuery?.split('&')
        ?.firstOrNull { it.substringBefore('=') == "loginName" }
        ?.substringAfter('=', "")
        ?.let { runCatching { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrNull() }
        ?.takeIf { it.isNotBlank() }

    suspend fun login(api: IclassAuthApi, vpn: Boolean, base: HttpUrl = baseUrl(vpn)): IclassLoginResponse {
        val jump = api.jumpMyCenter()
        val name = try {
            if (!jump.isSuccessful) throw IOException("iClass 跳转失败：HTTP ${jump.code()}")
            val finalUrl = jump.raw().request.url
            if (!isLanding(finalUrl, base)) throw IclassSessionExpiredException(vpn)
            loginName(finalUrl) ?: throw IclassSessionExpiredException(vpn)
        } finally {
            jump.body()?.close()
            jump.errorBody()?.close()
        }
        val response = api.login(name)
        val user = response.result ?: return response
        if (user.id.isNullOrBlank()) throw IOException("iClass 登录响应缺少用户标识")
        return response.copy(result = user.copy(
            sessionId = user.sessionId.takeUnless { it.isNullOrBlank() } ?: name,
            vpnMode = vpn
        ))
    }
}
