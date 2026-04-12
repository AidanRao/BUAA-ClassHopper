package top.aidanrao.buaa_classhopper.data.api

import top.aidanrao.buaa_classhopper.data.model.Result
import top.aidanrao.buaa_classhopper.data.model.ApiException
import top.aidanrao.buaa_classhopper.data.model.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object CommonRequest {
    
    suspend inline fun <T> safeApiCall(
        crossinline apiCall: suspend () -> Response<T>
    ): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiCall()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        Result.success(body)
                    } else {
                        Result.error(IOException("Response body is null"), "响应数据为空")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Result.error(
                        HttpException(response),
                        "请求失败: ${response.code()} - ${errorBody ?: response.message()}"
                    )
                }
            } catch (e: Exception) {
                Result.error(e, mapExceptionToMessage(e))
            }
        }
    }

    suspend inline fun <T, R> safeApiCallWithResponse(
        crossinline apiCall: suspend () -> ApiResponse<T>,
        crossinline transform: (T?) -> R?
    ): Result<R> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiCall()
                if (response.isSuccess) {
                    val transformed = transform(response.data)
                    if (transformed != null) {
                        Result.success(transformed)
                    } else {
                        Result.error(IOException("Transformed data is null"), "数据转换失败")
                    }
                } else {
                    Result.error(
                        ApiException(response.code, response.msg),
                        response.msg
                    )
                }
            } catch (e: Exception) {
                Result.error(e, mapExceptionToMessage(e))
            }
        }
    }

    suspend inline fun <T> safeApiCallWithResponse(
        crossinline apiCall: suspend () -> ApiResponse<T>
    ): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiCall()
                if (response.isSuccess && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.error(
                        ApiException(response.code, response.msg),
                        response.msg
                    )
                }
            } catch (e: Exception) {
                Result.error(e, mapExceptionToMessage(e))
            }
        }
    }

    fun mapExceptionToMessage(e: Exception): String {
        return when (e) {
            is SocketTimeoutException -> "网络连接超时，请检查网络后重试"
            is UnknownHostException -> "无法连接到服务器，请检查网络连接"
            is IOException -> "网络错误: ${e.message}"
            is HttpException -> {
                when (e.code()) {
                    400 -> "请求参数错误"
                    401 -> "未授权，请重新登录"
                    403 -> "访问被拒绝"
                    404 -> "请求的资源不存在"
                    500 -> "服务器内部错误"
                    502 -> "网关错误"
                    503 -> "服务暂不可用"
                    else -> "网络请求失败: ${e.code()}"
                }
            }
            is ApiException -> e.message
            else -> "未知错误: ${e.message}"
        }
    }
}
