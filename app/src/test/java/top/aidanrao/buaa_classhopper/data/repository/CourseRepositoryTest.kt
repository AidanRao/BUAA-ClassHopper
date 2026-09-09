package top.aidanrao.buaa_classhopper.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import retrofit2.Response
import top.aidanrao.buaa_classhopper.data.api.*
import top.aidanrao.buaa_classhopper.data.model.Result
import top.aidanrao.buaa_classhopper.data.model.dto.*
import top.aidanrao.buaa_classhopper.data.vpn.*

class CourseRepositoryTest {
    @Test fun rejectedStoredTokenInvalidatesSessionWithoutFallback() = runBlocking {
        val preferences = mock(VpnPreferences::class.java)
        `when`(preferences.getLoginName(false)).thenReturn("expired-token")
        val auth = mock(IclassAuthApi::class.java)
        `when`(auth.login("expired-token")).thenReturn(IclassLoginResponse(ERRMSG = "expired"))
        val fallback = mock(FallbackApi::class.java)
        val repo = repository(preferences, mock(IclassNetworkSelector::class.java), auth, Auth(true),
            Business(), Business(), fallback)
        assertTrue(repo.login(false) is Result.Error)
        verify(preferences).setSessionReady(false, false)
        verify(preferences, never()).saveLoginName(false, "expired-token")
        verifyNoInteractions(fallback)
    }

    @Test fun callbackAndStoredTokenBothBypassJumpAndRemainRouteScoped() = runBlocking {
        for (vpn in listOf(false, true)) {
            val preferences = mock(VpnPreferences::class.java)
            val selector = mock(IclassNetworkSelector::class.java)
            `when`(selector.useVpn()).thenReturn(vpn)
            val directAuth = Auth(false, onJump = { fail("Must use handed-off or stored token") })
            val vpnAuth = Auth(true, onJump = { fail("Must use handed-off or stored token") })
            val repo = repository(preferences, selector, directAuth, vpnAuth,
                Business(), Business(), mock(FallbackApi::class.java))
            assertTrue(repo.login(vpn, "callback-token") is Result.Success)
            verify(preferences).saveLoginName(vpn, "callback-token")
            `when`(preferences.getLoginName(vpn)).thenReturn("callback-token")
            assertTrue(repo.login() is Result.Success)
            verify(preferences, times(2)).saveLoginName(vpn, "callback-token")
            verify(preferences, never()).getLoginName(!vpn)
            verify(preferences, never()).saveLoginName(!vpn, "callback-token")
        }
    }

    private class Auth(private val vpn: Boolean, private val error: Exception? = null, private val onJump: () -> Unit = {}) : IclassAuthApi {
        override suspend fun jumpMyCenter(type: String): Response<ResponseBody> {
            error?.let { throw it }
            onJump()
            val raw = okhttp3.Response.Builder().request(Request.Builder()
                .url(IclassSession.baseUrl(vpn).newBuilder().addQueryParameter("loginName", "token").build()).build())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
            return Response.success("html".toResponseBody(), raw)
        }
        override suspend fun login(phone: String, password: String, verificationType: String, verificationUrl: String, userLevel: String) =
            IclassLoginResponse(result = IclassLoginResult("user", "session", "name", "school"))
    }

    private class Business : IclassApi {
        var signCalls = 0
        override suspend fun signClass(courseSchedId: Int, timestamp: Long, userId: String): IclassSignResponse {
            signCalls++
            return IclassSignResponse("ok", null)
        }
        override suspend fun getCourseSchedule(dateStr: String, userId: String, sessionId: String) =
            IclassScheduleResponse("2", 0, emptyList())
    }

    private fun repository(preferences: VpnPreferences, selector: IclassNetworkSelector, directAuth: IclassAuthApi, vpnAuth: IclassAuthApi,
                           direct: IclassApi, vpn: IclassApi, fallback: FallbackApi): CourseRepository {
        val context = mock(Context::class.java)
        val settings = mock(SharedPreferences::class.java)
        `when`(context.getSharedPreferences("course_checkin_settings", Context.MODE_PRIVATE)).thenReturn(settings)
        `when`(settings.getBoolean("fallback_enabled", false)).thenReturn(true)
        return CourseRepository(direct, vpn, directAuth, vpnAuth, fallback, mock(TokenManager::class.java), preferences, selector, context)
    }

    @Test fun expiredSessionNeverUsesFallbackEvenWhenEnabled() = runBlocking {
        for (vpn in listOf(false, true)) {
            val preferences = mock(VpnPreferences::class.java)
            val selector = mock(IclassNetworkSelector::class.java)
            `when`(selector.useVpn()).thenReturn(vpn)
            val fallback = mock(FallbackApi::class.java)
            val direct = Business()
            val vpnApi = Business()
            val error = IclassSessionExpiredException(vpn)
            val repo = repository(preferences, selector, Auth(false, error), Auth(true, error), direct, vpnApi, fallback)
            val result = repo.signClass(100)
            assertTrue(result is Result.Error)
            assertSame(error, (result as Result.Error).exception)
            assertEquals(IclassSessionExpiredException.messageFor(vpn), result.message)
            verifyNoInteractions(fallback)
            assertEquals(0, direct.signCalls + vpnApi.signCalls)
            verify(preferences).setSessionReady(vpn, false)
        }
    }

    @Test fun networkModeIsCapturedBeforeLoginAndSign() = runBlocking {
        val preferences = mock(VpnPreferences::class.java)
        val selector = mock(IclassNetworkSelector::class.java)
        `when`(selector.useVpn()).thenReturn(true, false)
        val fallback = mock(FallbackApi::class.java)
        val direct = Business()
        val vpn = Business()
        val repo = repository(preferences, selector, Auth(false), Auth(true), direct, vpn, fallback)
        assertTrue(repo.signClass(100) is Result.Success)
        assertEquals(0, direct.signCalls)
        assertEquals(1, vpn.signCalls)
        verify(selector, times(1)).useVpn()
        verifyNoInteractions(fallback)
    }
}
