package top.aidanrao.buaa_classhopper.data.vpn

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import top.aidanrao.buaa_classhopper.data.api.IclassAuthApi
import java.io.IOException

class IclassSessionTest {
    private fun api(server: MockWebServer, client: OkHttpClient, path: String = "/") =
        Retrofit.Builder().baseUrl(server.url(path)).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(IclassAuthApi::class.java)

    private fun user(session: String = "") = MockResponse().setHeader("Content-Type", "application/json")
        .setBody("""{"result":{"id":"42","sessionId":"$session","realName":"Test","academyName":"School"}}""")

    @Test fun followsRedirectsAndCarriesCookiesAndEncodesTokenOnce() = runBlocking {
        for (vpn in listOf(false, true)) {
            MockWebServer().use { server ->
                val path = if (vpn) VpnEndpoints.VPN_PATH_8346 else "/"
                val base = server.url(path)
                val token = "AB+/C=="
                server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "${path}hop")
                    .setHeader("Set-Cookie", "sso=active; Path=/; HttpOnly"))
                server.enqueue(MockResponse().setResponseCode(302)
                    .setHeader("Location", "${path}?type=jumpMyCenter&loginName=AB%2B%2FC%3D%3D"))
                server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>center</html>"))
                server.enqueue(user())
                var cookies = listOf<Cookie>()
                val client = OkHttpClient.Builder().cookieJar(object : CookieJar {
                    override fun loadForRequest(url: HttpUrl) = cookies.filter { it.matches(url) }
                    override fun saveFromResponse(url: HttpUrl, received: List<Cookie>) { cookies = received }
                }).addInterceptor(VpnSessionInterceptor(vpn, base) { fail("Normal HTML landing is valid") }).build()
                val result = IclassSession.login(api(server, client, path), vpn, base).result!!
                assertEquals(token, result.sessionId)
                assertEquals(vpn, result.vpnMode)
                assertEquals("42", result.id)
                assertEquals("${path}?type=jumpMyCenter", server.takeRequest().path)
                assertEquals("sso=active", server.takeRequest().getHeader("Cookie"))
                server.takeRequest()
                val login = server.takeRequest()
                assertEquals("${path}eschool/app/user/login_buaa.do", login.requestUrl!!.encodedPath)
                assertEquals(token, login.requestUrl!!.queryParameter("phone"))
                assertEquals("", login.requestUrl!!.queryParameter("password"))
                assertEquals("2", login.requestUrl!!.queryParameter("verificationType"))
                assertEquals("1", login.requestUrl!!.queryParameter("userLevel"))
                assertNull(login.getHeader("Authorization"))
            }
        }
    }

    @Test fun preservesServerSession() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/?loginName=token&type=jumpMyCenter"))
            server.enqueue(MockResponse().setBody("HTML"))
            server.enqueue(user("server-session"))
            assertEquals("server-session", IclassSession.login(api(server, OkHttpClient()), false, server.url("/")).result!!.sessionId)
        }
    }

    @Test fun queryDecodingPreservesLiteralPlusAndOnlyDecodesOnce() {
        for (query in listOf("loginName=AB+/C==&type=x", "type=x&loginName=AB%2B%2FC%3D%3D")) {
            assertEquals("AB+/C==", IclassSession.loginName("https://example.test/?$query".toHttpUrl()))
        }
        assertEquals("%2B", IclassSession.loginName("https://example.test/?loginName=%252B".toHttpUrl()))
        assertNull(IclassSession.loginName("https://example.test/?loginName=".toHttpUrl()))
        assertNull(IclassSession.loginName("https://example.test/?loginName=%ZZ".toHttpUrl()))
    }

    @Test fun missingTokenAndSsoRedirectStopBeforeApiLogin() = runBlocking {
        for (location in listOf("/?type=jumpMyCenter", "/login?loginName=untrusted")) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", location))
                server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("login"))
                var expired = false
                val client = OkHttpClient.Builder().addInterceptor(VpnSessionInterceptor(true, server.url("/")) { expired = true }).build()
                try {
                    IclassSession.login(api(server, client), true, server.url("/"))
                    fail("Expected session failure")
                } catch (e: IclassSessionExpiredException) { assertTrue(e.vpn) }
                assertTrue(expired)
                assertEquals(2, server.requestCount)
            }
        }
    }

    @Test fun httpFailureIsNotAnExpiredSession() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Content-Type", "text/html"))
            val client = OkHttpClient.Builder().addInterceptor(VpnSessionInterceptor(false, server.url("/")) { fail("Not auth failure") }).build()
            try {
                IclassSession.login(api(server, client), false, server.url("/"))
                fail("Expected HTTP failure")
            } catch (e: IOException) {
                assertFalse(e is IclassSessionExpiredException)
                assertTrue(e.message!!.contains("503"))
            }
        }
    }

    @Test fun businessHtmlAndUnauthorizedResponsesExpireSession() {
        for (code in listOf(200, 401, 403)) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "text/html"))
                var expired = false
                val client = OkHttpClient.Builder().addInterceptor(VpnSessionInterceptor(false, server.url("/")) { expired = true }).build()
                try {
                    client.newCall(okhttp3.Request.Builder().url(server.url("/app/course/query")).build()).execute().close()
                    fail("Expected authentication error")
                } catch (e: IclassSessionExpiredException) { assertFalse(e.vpn) }
                assertTrue(expired)
            }
        }
    }

    @Test fun missingUserRemainsBusinessFailureAndBlankIdIsRejected() = runBlocking {
        for (body in listOf("""{"ERRMSG":"denied"}""", """{"result":{"id":""}}""")) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/?loginName=token"))
                server.enqueue(MockResponse())
                server.enqueue(MockResponse().setBody(body))
                if (body.contains("ERRMSG")) {
                    val result = IclassSession.login(api(server, OkHttpClient()), false, server.url("/"))
                    assertNull(result.result)
                    assertEquals("denied", result.ERRMSG)
                } else {
                    try {
                        IclassSession.login(api(server, OkHttpClient()), false, server.url("/"))
                        fail("Expected invalid user")
                    } catch (e: IOException) { assertFalse(e is IclassSessionExpiredException) }
                }
            }
        }
    }

    @Test fun vpnPortalMayUseANonRootPath() {
        assertTrue(IclassSession.isVpnPortal("https://d.buaa.edu.cn/web/1/index".toHttpUrl()))
        assertFalse(IclassSession.isVpnPortal(VpnEndpoints.VPN_CAS_LOGIN_URL.toHttpUrl()))
        assertFalse(IclassSession.isVpnPortal(VpnEndpoints.ICLASS_VPN_8346.toHttpUrl()))
        assertFalse(IclassSession.isVpnPortal("https://d.buaa.edu.cn/login?cas_login=true".toHttpUrl()))
    }

    @Test fun productionOriginsAndLandingValidation() {
        assertEquals(8346, IclassSession.baseUrl(false).port)
        assertEquals("d.buaa.edu.cn", IclassSession.baseUrl(true).host)
        assertEquals(VpnEndpoints.VPN_PATH_8346, IclassSession.baseUrl(true).encodedPath)
        assertFalse(IclassSession.isLanding("https://evil.test/?loginName=x".toHttpUrl(), IclassSession.baseUrl(false)))
        assertFalse(IclassSession.isLanding(VpnEndpoints.ICLASS_DIRECT_8347.toHttpUrl(), IclassSession.baseUrl(false)))
    }
}
