package top.aidanrao.buaa_classhopper.data.repository

import top.aidanrao.buaa_classhopper.data.model.IclassAccessPolicy
import top.aidanrao.buaa_classhopper.data.model.IclassAccessException
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
        val repo = repository(preferences, directSelector(), auth, Auth(true),
            Business(), Business(), fallback)
        assertTrue(repo.login(false) is Result.Error)
        verify(preferences).setSessionReady(false, false)
        verify(preferences, never()).saveLoginName(false, "expired-token")
        verifyNoInteractions(fallback)
    }

    @Test fun callbackAndStoredTokenBothBypassJumpAndRemainRouteScoped() = runBlocking {
        for (vpn in listOf(false, true)) {
            val preferences = mock(VpnPreferences::class.java)
            val selector = directSelector()
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

    private fun directSelector(): IclassNetworkSelector = runBlocking {
        mock(IclassNetworkSelector::class.java).also { `when`(it.useVpn()).thenReturn(false) }
    }

    private fun repository(preferences: VpnPreferences, selector: IclassNetworkSelector, directAuth: IclassAuthApi, vpnAuth: IclassAuthApi,
                           direct: IclassApi, vpn: IclassApi, fallback: FallbackApi,
                           policy: IclassAccessPolicyRepository = allowedPolicy(),
                           tokens: TokenManager = mock(TokenManager::class.java)): CourseRepository {
        val context = mock(Context::class.java)
        val settings = mock(SharedPreferences::class.java)
        `when`(context.getSharedPreferences("course_checkin_settings", Context.MODE_PRIVATE)).thenReturn(settings)
        `when`(settings.getBoolean("fallback_enabled", false)).thenReturn(true)
        return CourseRepository(direct, vpn, directAuth, vpnAuth, fallback, tokens, preferences, selector, policy, context)
    }

    private fun allowedPolicy(json: String = """{"schemaVersion":1,"revision":"test","studentIds":[],"names":["name"]}"""): IclassAccessPolicyRepository =
        AccessPolicyFixture().repository().also { it.loadLocal(json) }

    @Test fun deniedIdentityPreservesSsoAndNeverCallsBusinessOrFallback() = runBlocking {
        for (vpn in listOf(false, true)) {
            val preferences = mock(VpnPreferences::class.java)
            val selector = directSelector()
            `when`(selector.useVpn()).thenReturn(vpn)
            val direct = mock(IclassApi::class.java)
            val viaVpn = mock(IclassApi::class.java)
            val fallback = mock(FallbackApi::class.java)
            val denied = allowedPolicy("""{"schemaVersion":1,"revision":"empty","studentIds":[],"names":[]}""")
            val repo = repository(preferences, selector, Auth(false), Auth(true), direct, viaVpn, fallback, denied)
            val login = repo.login(vpn, "callback-token")
            assertTrue((login as Result.Error).exception is IclassAccessException)
            verify(preferences).saveLoginName(vpn, "callback-token")
            verify(preferences).setSessionReady(vpn, true)
            verify(preferences, never()).setSessionReady(vpn, false)
            assertTrue(repo.signClass(100) is Result.Error)
            assertTrue(repo.getCourseSchedule(IclassLoginResult("id", "session", "name", "school", vpn), "20260910") is Result.Error)
            verifyNoInteractions(direct, viaVpn, fallback)
        }
    }

    @Test fun loginNetworkFailureNeverUsesFallback() = runBlocking {
        val fallback = mock(FallbackApi::class.java)
        val direct = mock(IclassApi::class.java)
        val vpn = mock(IclassApi::class.java)
        val repo = repository(mock(VpnPreferences::class.java), directSelector(),
            Auth(false, java.io.IOException("offline")), Auth(true), direct, vpn, fallback)
        assertTrue(repo.signClass(1) is Result.Error)
        verifyNoInteractions(direct, vpn, fallback)
    }

    private fun revocablePolicy(): IclassAccessPolicyRepository {
        return AccessPolicyFixture(remote = { IclassAccessPolicy(1, "revoke", emptySet(), emptySet()) })
            .repository().also {
                it.loadLocal("""{"schemaVersion":1,"revision":"allow","studentIds":[],"names":["name"]}""")
            }
    }

    @Test fun refreshedPolicyBlocksAnAlreadyAuthenticatedSchedule() = runBlocking {
        for (vpn in listOf(false, true)) {
            val policy = revocablePolicy()
            val business = mock(IclassApi::class.java)
            val fallback = mock(FallbackApi::class.java)
            val repo = repository(mock(VpnPreferences::class.java), directSelector(),
                Auth(false), Auth(true), business, business, fallback, policy)
            val identity = repo.login(vpn, "token").getOrThrow().result!!
            assertTrue(policy.refresh())
            assertTrue(repo.getCourseSchedule(identity, "20260910") is Result.Error)
            verifyNoInteractions(business, fallback)
        }
    }

    @Test fun fallbackRechecksRevocationAfterPrimaryFailure() = runBlocking {
        for (sign in listOf(false, true)) {
            val policy = revocablePolicy()
            val fallback = mock(FallbackApi::class.java)
            val tokens = mock(TokenManager::class.java)
            `when`(tokens.getValidToken()).thenReturn("token")
            val business = object : IclassApi {
                override suspend fun signClass(courseSchedId: Int, timestamp: Long, userId: String): IclassSignResponse {
                    policy.refresh()
                    return IclassSignResponse(null, "failed")
                }
                override suspend fun getCourseSchedule(dateStr: String, userId: String, sessionId: String): IclassScheduleResponse {
                    policy.refresh()
                    throw java.io.IOException("offline")
                }
            }
            val repo = repository(mock(VpnPreferences::class.java), directSelector(),
                Auth(false), Auth(true), business, business, fallback, policy, tokens)
            val identity = repo.login(false, "token").getOrThrow().result!!
            val result = if (sign) repo.signClass(1) else repo.getCourseSchedule(identity, "20260910")
            assertTrue(result.toString(), (result as Result.Error).exception is IclassAccessException)
            verifyNoInteractions(fallback)
        }
    }

    @Test fun authorizedRequestsCanStillUseFallback() = runBlocking {
        val fallback = mock(FallbackApi::class.java)
        val tokens = mock(TokenManager::class.java)
        `when`(tokens.getValidToken()).thenReturn("token")
        `when`(fallback.signClass(1, "token")).thenReturn(Response.success(Unit))
        `when`(fallback.getCourseSchedule("2026-09-10", "token")).thenReturn(
            FallbackScheduleResponse(1, "ok", FallbackScheduleData("ok", 0, emptyList())))
        val business = object : IclassApi {
            override suspend fun signClass(courseSchedId: Int, timestamp: Long, userId: String) = IclassSignResponse(null, "failed")
            override suspend fun getCourseSchedule(dateStr: String, userId: String, sessionId: String): IclassScheduleResponse =
                throw java.io.IOException("offline")
        }
        val repo = repository(mock(VpnPreferences::class.java), directSelector(),
            Auth(false), Auth(true), business, business, fallback, allowedPolicy(), tokens)
        val identity = repo.login(false, "token").getOrThrow().result!!
        assertTrue(repo.getCourseSchedule(identity, "20260910") is Result.Success)
        assertTrue(repo.signClass(1) is Result.Success)
        verify(fallback).getCourseSchedule("2026-09-10", "token")
        verify(fallback).signClass(1, "token")
        Unit
    }

    @Test fun expiredSessionNeverUsesFallbackEvenWhenEnabled() = runBlocking {
        for (vpn in listOf(false, true)) {
            val preferences = mock(VpnPreferences::class.java)
            val selector = directSelector()
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
        val selector = directSelector()
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
