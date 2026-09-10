package top.aidanrao.buaa_classhopper.data.api.interceptor

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mockStatic
import top.aidanrao.buaa_classhopper.di.NetworkModule

class LoggingInterceptorTest {
    @Test fun policyLogsRequestAndStatusWithoutReadingOrLoggingBody() {
        val logs = mutableListOf<String>()
        mockStatic(Log::class.java) { invocation ->
            invocation.arguments.filterIsInstance<String>().drop(1).forEach(logs::add)
            0
        }.use {
            MockWebServer().use { server ->
                val body = """{"code":1,"msg":"ok","data":{"names":["private-name"]}}"""
                server.enqueue(MockResponse().setBody(body))
                val client = NetworkModule.provideIclassAccessPolicyClient()
                client.newCall(Request.Builder().url(server.url("/v1/iclass/access-policy")).build()).execute().use { response ->
                    assertEquals(body, response.body!!.string())
                }
                assertTrue(logs.any { it.contains("GET") && it.contains("/v1/iclass/access-policy") })
                assertTrue(logs.any { it.contains("200 OK") })
                assertTrue(logs.any { it.contains("[omitted]") })
                assertFalse(logs.any { it.contains("private-name") })
            }
        }
    }

    @Test fun otherClientsStillLogResponseBodyByDefault() {
        val logs = mutableListOf<String>()
        mockStatic(Log::class.java) { invocation ->
            invocation.arguments.filterIsInstance<String>().drop(1).forEach(logs::add)
            0
        }.use {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("ordinary-response"))
                OkHttpClient.Builder().addInterceptor(LoggingInterceptor()).build()
                    .newCall(Request.Builder().url(server.url("/")).build()).execute().close()
                assertTrue(logs.any { it.contains("ordinary-response") })
            }
        }
    }
}
