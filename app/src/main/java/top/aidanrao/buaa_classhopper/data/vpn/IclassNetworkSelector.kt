package top.aidanrao.buaa_classhopper.data.vpn

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume

/** Select the usable route, without credentials, cookies or SSO redirects. */
@Singleton
class IclassNetworkSelector @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .callTimeout(2500, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun useVpn(): Boolean = selectRoute(::reachable)

    // Re-evaluate on every operation, so switching between Wi-Fi and mobile data takes effect.
    internal suspend fun selectRoute(probe: suspend (String) -> Boolean): Boolean = coroutineScope {
        val login = async { probe(VpnEndpoints.ICLASS_DIRECT_8346) }
        val business = async { probe(VpnEndpoints.ICLASS_DIRECT_8347) }
        val loginReachable = login.await()
        val businessReachable = business.await()
        !(loginReachable && businessReachable)
    }

    internal suspend fun reachable(url: String): Boolean = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).get().build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(false)
            }
            override fun onResponse(call: Call, response: Response) {
                // A root 404/405 still proves the direct service is reachable.
                val reachable = response.use { it.code in 200..499 && it.code != 403 }
                if (continuation.isActive) continuation.resume(reachable)
            }
        })
    }
}
