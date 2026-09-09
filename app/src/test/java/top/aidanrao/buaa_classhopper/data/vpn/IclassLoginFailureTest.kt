package top.aidanrao.buaa_classhopper.data.vpn

import com.google.gson.JsonSyntaxException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import top.aidanrao.buaa_classhopper.data.api.IclassAuthApi
import top.aidanrao.buaa_classhopper.data.model.dto.IclassLoginResponse

class IclassLoginFailureTest {
    @Test fun distinguishesCausesWithoutExposingRawExceptionMessages() {
        val cases = listOf(
            UnknownHostException("secret") to "域名解析失败",
            ConnectException("secret") to "无法连接服务器",
            SocketTimeoutException("secret") to "请求超时",
            SSLHandshakeException("secret") to "TLS/证书校验失败",
            JsonSyntaxException("secret") to "服务器响应格式错误或内容不完整",
            HttpException(Response.error<Any>(502, "secret".toResponseBody())) to "服务器返回 HTTP 502"
        )
        for ((cause, reason) in cases) {
            val error = IclassLoginFailure(IclassLoginFailure.JUMP, false, cause)
            assertEquals("iClass 直连登录失败（获取 loginName）：$reason", error.message)
            assertSame(cause, error.cause)
            assertFalse(error.message!!.contains("secret"))
        }
    }

    @Test fun serverRejectionRetainsReasonAndCodesButRedactsCredentials() {
        val error = IclassLoginFailure.rejected(IclassLoginResponse(
            STATUS = "1", ERRCODE = "403", ERRMSG = "账号受限 sessionId=private"
        ), true)
        assertTrue(error.message!!.contains("VPN登录失败（调用登录接口）"))
        assertTrue(error.message!!.contains("账号受限"))
        assertTrue(error.message!!.contains("STATUS=1"))
        assertTrue(error.message!!.contains("ERRCODE=403"))
        assertFalse(error.message!!.contains("private"))
        assertTrue(IclassLoginFailure.rejected(IclassLoginResponse(), false).message!!.contains("缺少用户信息 result"))
    }

    @Test fun reportsApiStageAfterSuccessfulJump() = runBlocking {
        for (invalidJson in listOf(false, true)) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/?loginName=private"))
                server.enqueue(MockResponse().setBody("landing"))
                server.enqueue(if (invalidJson) MockResponse().setBody("<html>invalid</html>")
                    else MockResponse().setResponseCode(500))
                val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
                    .addConverterFactory(GsonConverterFactory.create()).build().create(IclassAuthApi::class.java)
                try {
                    IclassSession.login(api, true, server.url("/"))
                    fail("Expected login failure")
                } catch (e: IclassLoginFailure) {
                    assertEquals(IclassLoginFailure.API_LOGIN, e.stage)
                    assertTrue(e.message, e.message!!.contains(if (invalidJson) "响应格式错误" else "HTTP 500"))
                    assertFalse(e.message!!.contains("private"))
                }
            }
        }
    }
}
