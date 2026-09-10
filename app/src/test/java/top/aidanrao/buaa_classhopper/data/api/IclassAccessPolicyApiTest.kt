package top.aidanrao.buaa_classhopper.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import top.aidanrao.buaa_classhopper.data.api.interceptor.LoggingInterceptor
import okhttp3.OkHttpClient
import com.google.gson.Gson
import top.aidanrao.buaa_classhopper.data.repository.AccessPolicyFixture

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import top.aidanrao.buaa_classhopper.di.NetworkModule

class IclassAccessPolicyApiTest {
    private val body = """{"code":1,"msg":"ok","data":{"schemaVersion":1,"revision":"remote","studentIds":["001"],"names":[]}}"""

    private fun api(baseUrl: String, client: OkHttpClient): IclassAccessPolicyApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(Gson()))
        .build()
        .create(IclassAccessPolicyApi::class.java)

    private fun withServer(block: suspend (MockWebServer, IclassAccessPolicyApi) -> Unit) = runBlocking {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.start()
            val client = NetworkModule.provideIclassAccessPolicyClient().newBuilder()
                .apply { interceptors().removeAll { it is LoggingInterceptor } }
                .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
                .callTimeout(500, TimeUnit.MILLISECONDS).build()
            block(server, api(server.url("/api/buaa-classhopper/").toString(), client))
        }
    }

    @Test fun fetchesCommonResponseWithoutCredentials() = withServer { server, api ->
        server.enqueue(MockResponse().setBody(body))
        val response = api.getAccessPolicy()
        assertEquals(200, response.code())
        assertEquals(listOf("001"), response.body()!!.getDataOrThrow().studentIds)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/buaa-classhopper/v1/iclass/access-policy", request.path)
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("Cookie"))
        assertEquals(0L, request.bodySize)
    }

    @Test fun failedRefreshesKeepPreviouslyAllowedPolicy() = withServer { server, api ->
        val cached = """{"schemaVersion":1,"revision":"cached","studentIds":["001"],"names":[]}"""
        val repo = AccessPolicyFixture(cached).repository(api)
        repo.loadLocal()
        listOf(
            MockResponse().setResponseCode(500).setBody(body),
            MockResponse().setBody("""{"code":0,"msg":"failed","data":null}"""),
            MockResponse().setBody("""{"code":1,"msg":"ok","data":null}"""),
            MockResponse().setBody("not json"),
            MockResponse().setBody("""{"code":1,"msg":"ok","data":{}}"""),
            MockResponse().setBody(body.replace("\"names\":[]", "\"names\":[null]")),
            MockResponse().setBody(body).setBodyDelay(2, TimeUnit.SECONDS)
        ).forEach {
            server.enqueue(it)
            assertFalse(repo.refresh())
            repo.requireAllowed("001", null)
        }
    }

    @Test fun rejectsUntrustedTls() = withServer { server, _ ->
        val client = NetworkModule.provideIclassAccessPolicyClient().newBuilder()
            .apply { interceptors().removeAll { it is LoggingInterceptor } }.build()
        assertThrows(Exception::class.java) { runBlocking { api(server.url("/").toString(), client).getAccessPolicy() } }
    }
}
