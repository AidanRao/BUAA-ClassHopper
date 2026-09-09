package top.aidanrao.buaa_classhopper.data.vpn

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class IclassNetworkSelectorTest {
    @Test fun campusRequiresBothLoginAndBusinessReachability() = runBlocking {
        val selector = IclassNetworkSelector()
        assertFalse(selector.selectRoute { true })
        assertTrue(selector.selectRoute { false })
        assertTrue(selector.selectRoute { it == VpnEndpoints.ICLASS_DIRECT_8346 })
        assertTrue(selector.selectRoute { it == VpnEndpoints.ICLASS_DIRECT_8347 })
    }

    @Test fun detectsNetworkChangesWithoutRetainingTheOldMode() = runBlocking {
        val selector = IclassNetworkSelector()
        assertFalse(selector.selectRoute { true })
        assertTrue(selector.selectRoute { false })
        assertFalse(selector.selectRoute { true })
    }

    @Test fun probeDoesNotFollowSsoRedirectOrSendCredentials() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://sso.buaa.edu.cn/login"))
            assertTrue(IclassNetworkSelector().reachable(server.url("/").toString()))
            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
        }
    }

    @Test fun blockedDirectServiceAndTimeoutChooseVpn() = runBlocking {
        MockWebServer().use { server ->
            val selector = IclassNetworkSelector()
            server.enqueue(MockResponse().setResponseCode(404))
            assertTrue(selector.reachable(server.url("/").toString()))
            server.enqueue(MockResponse().setResponseCode(403))
            assertFalse(selector.reachable(server.url("/").toString()))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertFalse(selector.reachable(server.url("/").toString()))
        }
    }
}
