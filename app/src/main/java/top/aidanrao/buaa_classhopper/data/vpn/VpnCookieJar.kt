package top.aidanrao.buaa_classhopper.data.vpn

import android.util.Log
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在 OkHttp 与 WebView CookieManager 之间同步 VPN cookie。
 *
 * - WebView 登录后 CookieManager 会持有 d.buaa.edu.cn 的 cookie；
 * - OkHttp 在发起请求时从 CookieManager 读取 cookie 作为 header；
 * - OkHttp 响应中如果 Set-Cookie 了新 cookie，再写回 CookieManager，使两端保持一致。
 *
 * 同时持久化到 [VpnPreferences] 以便进程重启后继续使用。
 */
@Singleton
class VpnCookieJar @Inject constructor(
    private val vpnPreferences: VpnPreferences
) : CookieJar {

    companion object {
        private const val TAG = "VpnCookieJar"
        private val SESSION_HOSTS = setOf(
            VpnEndpoints.VPN_HOST, "iclass.buaa.edu.cn", "sso.buaa.edu.cn", "uc.buaa.edu.cn"
        )
    }

    init {
        // 启动时把持久化的 cookie 注入 WebView CookieManager
        restoreCookiesFromPrefs()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (url.host !in SESSION_HOSTS) return emptyList()
        val cookieHeader = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        val cookies = mutableListOf<Cookie>()
        cookieHeader.split(";").forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return@forEach
            val parsed = Cookie.parse(url, "$trimmed; Path=/") ?: return@forEach
            cookies.add(parsed)
        }
        return cookies
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.host !in SESSION_HOSTS) return
        val manager = CookieManager.getInstance()
        cookies.forEach { cookie ->
            try {
                manager.setCookie(url.toString(), cookie.toString())
            } catch (e: Exception) {
                Log.w(TAG, "setCookie failed: ${cookie.name}", e)
            }
        }
        manager.flush()
        if (url.host == VpnEndpoints.VPN_HOST) persistCookies()
    }

    /** 将 WebView 当前的 d.buaa.edu.cn cookie 序列化并持久化。 */
    fun persistCookies() {
        val cookieHeader = CookieManager.getInstance()
            .getCookie("https://${VpnEndpoints.VPN_HOST}/")
        vpnPreferences.saveVpnCookies(cookieHeader)
    }

    /** 从持久化的字符串中恢复 cookie 到 WebView CookieManager。 */
    private fun restoreCookiesFromPrefs() {
        val stored = vpnPreferences.getVpnCookies() ?: return
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        stored.split(";").forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isNotEmpty()) {
                manager.setCookie("https://${VpnEndpoints.VPN_HOST}/", trimmed)
            }
        }
        manager.flush()
    }

    fun hasVpnCookies(): Boolean {
        val header = CookieManager.getInstance().getCookie("https://${VpnEndpoints.VPN_HOST}/")
        return !header.isNullOrBlank()
    }

    /** Expire only cookies belonging to the selected authentication mode. */
    fun clear(vpn: Boolean, then: () -> Unit = {}) {
        val manager = CookieManager.getInstance()
        val urls = if (vpn) listOf(
            "https://${VpnEndpoints.VPN_HOST}/", VpnEndpoints.VPN_CAS_LOGIN_URL,
            VpnEndpoints.ICLASS_VPN_8346, VpnEndpoints.ICLASS_VPN_8347
        ) else listOf("https://sso.buaa.edu.cn/login", "https://sso.buaa.edu.cn/",
            "https://uc.buaa.edu.cn/",
            VpnEndpoints.ICLASS_DIRECT_8346, VpnEndpoints.ICLASS_DIRECT_8347)
        val removals = mutableSetOf<Pair<String, String>>()
        urls.forEach { address ->
            val url = address.toHttpUrl()
            val paths = mutableSetOf("/", url.encodedPath)
            var path = url.encodedPath.substringBeforeLast('/', "")
            while (path.isNotEmpty()) {
                paths.add(path)
                paths.add("$path/")
                path = path.substringBeforeLast('/', "")
            }
            manager.getCookie(address)?.split(';')?.forEach { raw ->
                val name = raw.trim().substringBefore('=')
                paths.forEach { cookiePath ->
                    removals.add(address to "$name=; Max-Age=0; Path=$cookiePath")
                    removals.add(address to "$name=; Max-Age=0; Domain=${url.host}; Path=$cookiePath")
                }
            }
        }
        vpnPreferences.setSessionReady(vpn, false)
        if (vpn) vpnPreferences.clearVpnCookies()
        if (removals.isEmpty()) { then(); return }
        var remaining = removals.size
        removals.forEach { (url, cookie) ->
            manager.setCookie(url, cookie) {
                remaining -= 1
                if (remaining == 0) { manager.flush(); then() }
            }
        }
    }
}
