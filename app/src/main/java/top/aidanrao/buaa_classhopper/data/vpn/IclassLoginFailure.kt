package top.aidanrao.buaa_classhopper.data.vpn

import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import retrofit2.HttpException
import top.aidanrao.buaa_classhopper.data.model.dto.IclassLoginResponse

/** Controlled messages avoid leaking token-bearing URLs from transport/parser exceptions. */
class IclassLoginFailure(val stage: String, val vpn: Boolean, cause: Exception) : IOException(
    "iClass ${if (vpn) "VPN" else "直连"}登录失败（$stage）：${reason(cause)}", cause
) {
    companion object {
        const val JUMP = "获取 loginName"
        const val API_LOGIN = "调用登录接口"

        fun reason(error: Exception): String = when (error) {
            is IclassLoginFailure -> error.message.orEmpty()
            is InvalidIclassLoginResponse -> error.message.orEmpty()
            is UnknownHostException -> "域名解析失败"
            is SocketTimeoutException, is InterruptedIOException -> "请求超时"
            is ConnectException -> "无法连接服务器"
            is NoRouteToHostException -> "无法访问服务器所在网络"
            is SSLException -> "TLS/证书校验失败"
            is HttpException -> "服务器返回 HTTP ${error.code()}"
            is JsonParseException, is MalformedJsonException, is EOFException -> "服务器响应格式错误或内容不完整"
            is ProtocolException -> "HTTP 协议或重定向错误"
            is IOException -> "网络读写失败（${error.javaClass.simpleName}）"
            else -> "处理响应失败（${error.javaClass.simpleName}）"
        }

        fun rejected(response: IclassLoginResponse, vpn: Boolean): IclassLoginFailure {
            val detail = response.ERRMSG?.takeIf { it.isNotBlank() }
                ?: "登录响应缺少用户信息 result"
            val codes = listOfNotNull(
                response.STATUS?.let { "STATUS=$it" },
                response.ERRCODE?.let { "ERRCODE=$it" }
            ).joinToString("，")
            // Retain server explanations but do not expose authentication URLs or token values.
            val safeDetail = detail
                .replace(Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE), "[地址已隐藏]")
                .replace(Regex("(loginName|sessionId|phone|cookie|authorization)\\s*[:=]\\s*[^\\s,，;；]+", RegexOption.IGNORE_CASE), "$1=[已隐藏]")
                .take(300)
            return IclassLoginFailure(API_LOGIN, vpn, InvalidIclassLoginResponse(safeDetail + if (codes.isEmpty()) "" else "（$codes）"))
        }
    }
}

internal class InvalidIclassLoginResponse(message: String) : IOException(message)
