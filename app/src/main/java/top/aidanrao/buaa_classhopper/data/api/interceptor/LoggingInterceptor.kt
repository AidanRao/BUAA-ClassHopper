package top.aidanrao.buaa_classhopper.data.api.interceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class LoggingInterceptor @Inject constructor() : Interceptor {
    
    companion object {
        private const val TAG = "HttpLogger"
        private const val MAX_LOG_LENGTH = 4000
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        logRequest(request)
        
        val startTime = System.nanoTime()
        val response: Response
        
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            Log.e(TAG, "Request failed: ${request.url}", e)
            throw e
        }
        
        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000
        
        logResponse(response, durationMs)
        
        return response
    }

    private fun logRequest(request: okhttp3.Request) {
        val method = request.method
        val url = request.url
        val headers = request.headers
        
        Log.d(TAG, "┌────── Request ──────────────────────────────")
        Log.d(TAG, "│ $method $url")
        
        headers.forEach { (name, value) ->
            val maskedValue = maskSensitiveHeader(name, value)
            Log.d(TAG, "│ $name: $maskedValue")
        }
        
        request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            val charset = body.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
            val bodyString = buffer.readString(charset)
            if (bodyString.isNotEmpty()) {
                Log.d(TAG, "│ Body: ${bodyString.take(500)}")
            }
        }
        
        Log.d(TAG, "└──────────────────────────────────────────────")
    }

    private fun logResponse(response: Response, durationMs: Long) {
        val request = response.request
        val url = request.url
        
        Log.d(TAG, "┌────── Response (${durationMs}ms) ─────────────────────")
        Log.d(TAG, "│ ${response.code} ${response.message} $url")
        
        response.headers.forEach { (name, value) ->
            Log.d(TAG, "│ $name: $value")
        }
        
        val responseBody = response.body
        val source = responseBody?.source()
        source?.request(Long.MAX_VALUE)
        val buffer = source?.buffer
        
        val charset = responseBody?.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
        
        if (buffer != null) {
            val bodyString = buffer.clone().readString(charset)
            if (bodyString.isNotEmpty()) {
                logLongString(TAG, "│ Body: ", bodyString)
            }
        }
        
        Log.d(TAG, "└──────────────────────────────────────────────")
    }

    private fun logLongString(tag: String, prefix: String, content: String) {
        if (content.length <= MAX_LOG_LENGTH) {
            Log.d(tag, "$prefix$content")
            return
        }
        
        Log.d(tag, "$prefix${content.take(MAX_LOG_LENGTH)}")
        var remaining = content.drop(MAX_LOG_LENGTH)
        while (remaining.isNotEmpty()) {
            Log.d(tag, "│ ${remaining.take(MAX_LOG_LENGTH)}")
            remaining = remaining.drop(MAX_LOG_LENGTH)
        }
    }

    private fun maskSensitiveHeader(name: String, value: String): String {
        val sensitiveHeaders = listOf("authorization", "token", "cookie", "set-cookie")
        return if (name.lowercase() in sensitiveHeaders) {
            "${value.take(10)}..."
        } else {
            value
        }
    }
}
