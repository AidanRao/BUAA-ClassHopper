package com.example.hello.service

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.hello.utils.DeviceIdUtil
import com.example.hello.utils.SignUtils
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import javax.net.ssl.*

class ApiService(private val context: Context) {
    // 创建信任所有证书的OkHttpClient
    private val client = OkHttpClient.Builder()
        .sslSocketFactory(createSSLSocketFactory(), object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        .hostnameVerifier { _, _ -> true }
        .build()
    private val gson = Gson()
    
    // 创建信任所有证书的SSL Socket Factory
    private fun createSSLSocketFactory(): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }), SecureRandom())
        return sslContext.socketFactory
    }
    
    // 鉴权相关变量
    var token: String? = null
    private var expireAt: Long? = null
    private val APP_KEY = "buaa-classhopper-android"

    // 鉴权相关接口和数据模型
    interface OnAuthListener {
        fun onSuccess(token: String, expireAt: Long)
        fun onFailure(error: String)
    }
    
    data class AuthResponse(
        val code: Int,
        val msg: String,
        val data: AuthData?
    )
    
    data class AuthData(
        val token: String,
        val expireAt: String
    )
    
    data class AuthRequest(
        val appKey: String,
        val timestamp: Long,
        val signature: String,
        val appUUID: String
    )
    
    // 检查token是否有效
    private fun isTokenValid(): Boolean {
        if (token.isNullOrEmpty() || expireAt == null) {
            return false
        }
        return System.currentTimeMillis() < expireAt!!
    }
    
    // 获取鉴权token的方法
    fun getAuthToken(listener: OnAuthListener) {
        // 构建请求参数
        val timestamp = System.currentTimeMillis()
        val appUUID = DeviceIdUtil.getPersistentUUID(context)
        
        // 生成签名
        val params = mapOf(
            "appKey" to APP_KEY,
            "timestamp" to timestamp.toString(),
            "appUUID" to appUUID
        )
        val signature = SignUtils.generateSignature(params)
        
        val authRequest = AuthRequest(
            appKey = APP_KEY,
            timestamp = timestamp,
            signature = signature,
            appUUID = appUUID
        )
        
        // 构建请求体
        val jsonBody = gson.toJson(authRequest)
        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), jsonBody)
        
        // 构建请求
        val authUrl = "https://101.42.43.228/api/user/third-auth"
        val request = Request.Builder()
            .url(authUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        
        // 发送请求
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("获取鉴权token失败: ${e.message}")
                e.printStackTrace()
            }
            
            @SuppressLint("SimpleDateFormat")
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    Log.i("login", responseData.toString())
                    val authResponse = gson.fromJson(responseData, AuthResponse::class.java)
                    
                    if (authResponse.code == 1 && authResponse.data != null) {
                        // 保存token和过期时间
                        token = authResponse.data.token
                        // 解析ISO 8601格式的时间字符串
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
                            val date = sdf.parse(authResponse.data.expireAt.substringBeforeLast('Z'))
                            expireAt = date?.time ?: 0
                        } catch (e: Exception) {
                            // 如果解析失败，使用当前时间+2小时作为过期时间
                            expireAt = System.currentTimeMillis() + 2 * 60 * 60 * 1000
                            e.printStackTrace()
                        }
                        listener.onSuccess(authResponse.data.token, expireAt!!)
                    } else {
                        listener.onFailure("获取鉴权token失败: ${authResponse.msg}")
                    }
                } catch (e: Exception) {
                    listener.onFailure("解析鉴权响应失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }
}