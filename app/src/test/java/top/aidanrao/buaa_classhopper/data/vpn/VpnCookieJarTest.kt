package top.aidanrao.buaa_classhopper.data.vpn

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class VpnCookieJarTest {
    @Test fun sharesIdentityCenterCookiesInBothDirections() {
        val manager = mock(CookieManager::class.java)
        mockStatic(CookieManager::class.java).use { mocked ->
            mocked.`when`<CookieManager> { CookieManager.getInstance() }.thenReturn(manager)
            val jar = VpnCookieJar(mock(VpnPreferences::class.java))
            val url = "https://uc.buaa.edu.cn/".toHttpUrl()
            `when`(manager.getCookie(url.toString())).thenReturn("JSESSIONID=test-session; token=AB+/C==")
            val cookies = jar.loadForRequest(url)
            assertEquals(listOf("test-session", "AB+/C=="), cookies.map { it.value })
            assertTrue(cookies.all { it.matches(url) })
            val updated = Cookie.parse(url, "JSESSIONID=new-session; Path=/; Secure; HttpOnly")!!
            jar.saveFromResponse(url, listOf(updated))
            verify(manager).setCookie(url.toString(), updated.toString())
            verify(manager).flush()
        }
    }

    @Test fun doesNotReadOrWriteCookiesForOtherHosts() {
        val manager = mock(CookieManager::class.java)
        mockStatic(CookieManager::class.java).use { mocked ->
            mocked.`when`<CookieManager> { CookieManager.getInstance() }.thenReturn(manager)
            val jar = VpnCookieJar(mock(VpnPreferences::class.java))
            for (host in listOf("example.com", "uc.buaa.edu.cn.example.com", "other.buaa.edu.cn")) {
                val url = "https://$host/".toHttpUrl()
                assertTrue(jar.loadForRequest(url).isEmpty())
                jar.saveFromResponse(url, listOf(Cookie.parse(url, "session=test")!!))
            }
            verifyNoInteractions(manager)
        }
    }
}
