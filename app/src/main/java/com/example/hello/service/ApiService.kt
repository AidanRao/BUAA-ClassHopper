package com.example.hello.service

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.hello.utils.DeviceIdUtil
import com.example.hello.utils.SignUtils
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import javax.net.ssl.*
import androidx.core.content.edit

class ApiService(private val context: Context) {
    private val BASE_URL = "http://39.105.96.112/api/"
//    private val BASE_URL = "http://10.0.2.2:8088/"

    // 创建信任所有证书的OkHttpClient
    val client = OkHttpClient.Builder()
        .sslSocketFactory(createSSLSocketFactory(), object : X509TrustManager {
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        .hostnameVerifier { _, _ -> true }
        .build()
    private val gson = Gson()
    private val authPrefs = context.getSharedPreferences("AuthCache", Context.MODE_PRIVATE)
    
    // 创建信任所有证书的SSL Socket Factory
    private fun createSSLSocketFactory(): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(object : X509TrustManager {
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }), SecureRandom())
        return sslContext.socketFactory
    }
    
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
    
    init {
        val cached = readCachedToken()
        token = cached.first
        expireAt = cached.second
    }

    private fun readCachedToken(): Pair<String?, Long?> {
        val cachedToken = authPrefs.getString("token", null)
        val cachedExpireAt = if (authPrefs.contains("expireAt")) authPrefs.getLong("expireAt", 0L) else null
        return Pair(cachedToken, cachedExpireAt)
    }

    private fun saveCachedToken(token: String, expireAt: Long) {
        authPrefs.edit { putString("token", token).putLong("expireAt", expireAt) }
    }

    private fun isTokenValid(token: String?, expireAt: Long?): Boolean {
        if (token.isNullOrEmpty() || expireAt == null) {
            return false
        }
        return System.currentTimeMillis() < expireAt
    }

    fun getCachedToken(): String? {
        val cached = readCachedToken()
        token = cached.first
        expireAt = cached.second
        return if (isTokenValid(token, expireAt)) token else null
    }

    fun getValidToken(listener: OnAuthListener) {
        val cached = readCachedToken()
        token = cached.first
        expireAt = cached.second
        if (isTokenValid(token, expireAt)) {
            listener.onSuccess(token!!, expireAt!!)
            return
        }
        getAuthToken(listener)
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
        val requestBody =
            jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        
        // 构建请求
        val authUrl = BASE_URL + "user/third-auth"
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
                    val authResponse = gson.fromJson(responseData, AuthResponse::class.java)
                    
                    if (authResponse.code == 1 && authResponse.data != null) {
                        token = authResponse.data.token
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
                            val date = sdf.parse(authResponse.data.expireAt.substringBeforeLast('Z'))
                            expireAt = date?.time ?: 0
                        } catch (e: Exception) {
                            expireAt = System.currentTimeMillis() + 2 * 60 * 60 * 1000
                            e.printStackTrace()
                        }
                        saveCachedToken(token!!, expireAt!!)
                        listener.onSuccess(token!!, expireAt!!)
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
    
    // 用户信息相关接口和数据模型
    interface OnUserInfoListener {
        fun onSuccess(userInfo: UserInfoResponse.UserInfoData)
        fun onFailure(error: String)
    }
    
    data class UserInfoResponse(
        val code: Int,
        val msg: String,
        val data: UserInfoData?
    ) {
        data class UserInfoData(
            val id: String,
            val username: String,
            val avatar: String?,
            val createTime: String,
            val gender: String,
            val studentId: String,
            val verified: Boolean,
            val joinedTeamCount: Int,
            val createdTeamCount: Int,
            val role: String,
            val appKey: String?
        )
    }
    
    // 获取用户信息的方法
    fun getUserInfo(token: String, listener: OnUserInfoListener) {
        val userInfoUrl = BASE_URL + "user/info"
        val request = Request.Builder()
            .url(userInfoUrl)
            .get()
            .addHeader("Authorization", token)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("获取用户信息失败: ${e.message}")
                e.printStackTrace()
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    Log.i("UserInfo", responseData.toString())
                    val userInfoResponse = gson.fromJson(responseData, UserInfoResponse::class.java)
                    
                    if (userInfoResponse.code == 1 && userInfoResponse.data != null) {
                        listener.onSuccess(userInfoResponse.data)
                    } else {
                        listener.onFailure("获取用户信息失败: ${userInfoResponse.msg}")
                    }
                } catch (e: Exception) {
                    listener.onFailure("解析用户信息响应失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }
    
    // 公告相关接口和数据模型
    interface OnAnnouncementsListener {
        fun onSuccess(announcements: List<AnnouncementData>)
        fun onFailure(error: String)
    }
    
    // 公告详情监听器接口
    interface OnAnnouncementDetailListener {
        fun onSuccess(announcementDetail: AnnouncementDetailData)
        fun onFailure(error: String)
    }
    
    data class AnnouncementResponse(
        val code: Int,
        val msg: String,
        val data: List<AnnouncementData>
    )
    
    // 公告详情响应数据模型
    data class AnnouncementDetailResponse(
        val code: Int,
        val msg: String,
        val data: AnnouncementDetailData?
    )
    
    data class AnnouncementData(
        val id: String,
        val title: String,
        val content: String?,
        val posterUserId: String?,
        val cover: String?,
        val createTime: String,
        val updateTime: String?,
        val deleted: String?,
        val targetUserType: String?,
        val targetAppKeys: String?,
        val posterUsername: String?, // 发布者名称
        val posterAvatar: String?    // 发布者头像
    )
    
    // 公告详情数据模型
    data class AnnouncementDetailData(
        val id: String,
        val title: String,
        val content: String?,
        val posterUserId: String?,
        val cover: String?,
        val createTime: String,
        val updateTime: String?,
        val deleted: Boolean,
        val targetUserType: String?,
//        val targetAppKeys: String?,
        val posterUsername: String?,
        val posterAvatar: String?
    )
    
    // 获取公告列表的方法
    fun getAnnouncements(token: String, listener: OnAnnouncementsListener) {
        val announcementUrl = BASE_URL + "message/announcement/list"
        val request = Request.Builder()
            .url(announcementUrl)
            .get()
            .addHeader("Authorization", token)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("获取公告列表失败: ${e.message}")
                e.printStackTrace()
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    Log.i("Announcement", responseData.toString())
                    val announcementResponse = gson.fromJson(responseData, AnnouncementResponse::class.java)
                    
                    if (announcementResponse.code == 1) {
                        listener.onSuccess(announcementResponse.data)
                    } else {
                        listener.onFailure("获取公告列表失败: ${announcementResponse.msg}")
                    }
                } catch (e: Exception) {
                    listener.onFailure("解析公告响应失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }
    
    // 获取公告详情的方法
    fun getAnnouncementDetail(token: String, announcementId: String, listener: OnAnnouncementDetailListener) {
        val announcementDetailUrl = BASE_URL + "message/announcement/$announcementId"
        val request = Request.Builder()
            .url(announcementDetailUrl)
            .get()
            .addHeader("Authorization", token)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("获取公告详情失败: ${e.message}")
                e.printStackTrace()
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    Log.i("AnnouncementDetail", responseData.toString())
                    val announcementDetailResponse = gson.fromJson(responseData, AnnouncementDetailResponse::class.java)
                    
                    if (announcementDetailResponse.code == 1 && announcementDetailResponse.data != null) {
                        listener.onSuccess(announcementDetailResponse.data)
                    } else {
                        listener.onFailure("获取公告详情失败: ${announcementDetailResponse.msg}")
                    }
                } catch (e: Exception) {
                    listener.onFailure("解析公告详情响应失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }

    // 身份验证相关接口和数据模型
    interface OnVerifyCodeListener {
        fun onSuccess(message: String)
        fun onFailure(error: String)
    }

    interface OnUserVerifyListener {
        fun onSuccess(message: String)
        fun onFailure(error: String)
    }

    data class VerifyCodeResponse(
        val code: Int,
        val msg: String,
        val data: Any?
    )

    data class UserVerifyResponse(
        val code: Int,
        val msg: String,
        val data: Any?
    )

    data class VerifyCodeRequest(
        val email: String,
        val type: Int
    )

    data class UserVerifyRequest(
        val email: String,
        val verifyCode: String
    )

    // 发送验证码的方法
    fun sendVerifyCode(token: String, studentId: String, listener: OnVerifyCodeListener) {
        val verifyUrl = BASE_URL + "verify"
        val email = "$studentId@buaa.edu.cn"
        
        val verifyCodeRequest = VerifyCodeRequest(
            email = email,
            type = 4
        )
        
        // 构建请求体
        val jsonBody = gson.toJson(verifyCodeRequest)
        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), jsonBody)
        
        // 构建请求
        val request = Request.Builder()
            .url(verifyUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", token)
            .build()
        
        // 发送请求
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("发送验证码失败: ${e.message}")
                e.printStackTrace()
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    Log.i("VerifyCode", responseData.toString())
                    val verifyCodeResponse = gson.fromJson(responseData, VerifyCodeResponse::class.java)
                    
                    if (verifyCodeResponse.code == 1) {
                        listener.onSuccess(verifyCodeResponse.msg)
                    } else {
                        listener.onFailure("发送验证码失败: ${verifyCodeResponse.msg}")
                    }
                } catch (e: Exception) {
                    listener.onFailure("解析验证码响应失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }

    // 验证身份的方法
    fun verifyUser(token: String, studentId: String, verifyCode: String, listener: OnUserVerifyListener) {
        val verifyUserUrl = BASE_URL + "user/verify"
        val email = "$studentId@buaa.edu.cn"
        
        val userVerifyRequest = UserVerifyRequest(
            email = email,
            verifyCode = verifyCode
        )
        
        // 构建请求体
        val jsonBody = gson.toJson(userVerifyRequest)
        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), jsonBody)
        
        // 构建请求
        val request = Request.Builder()
            .url(verifyUserUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", token)
            .build()
        
        // 发送请求
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("身份验证失败: ${e.message}")
                e.printStackTrace()
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    Log.i("UserVerify", responseData.toString())
                    val userVerifyResponse = gson.fromJson(responseData, UserVerifyResponse::class.java)
                    
                    if (userVerifyResponse.code == 1) {
                        listener.onSuccess(userVerifyResponse.msg)
                    } else {
                        listener.onFailure("身份验证失败: ${userVerifyResponse.msg}")
                    }
                } catch (e: Exception) {
                    listener.onFailure("解析身份验证响应失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }

    // 扫码登录确认相关接口和监听器
    interface OnQRCodeLoginListener {
        fun onSuccess()
        fun onFailure(error: String)
    }

    interface OnQRCodeInfoListener {
        fun onSuccess(info: QRCodeInfoData)
        fun onFailure(error: String)
    }

    data class QRCodeInfoResponse(
        val code: Int,
        val msg: String,
        val data: QRCodeInfoData?
    )

    data class QRCodeInfoData(
        val status: String?,
        val token: String?,
        val expireAt: String?,
        val ipAddress: String?,
        val browser: String?,
        val os: String?,
        val device: String?,
        val requestTime: String?
    )

    fun getQRCodeInfo(scanId: String, listener: OnQRCodeInfoListener) {
        val url = BASE_URL + "user/auth/qrcode?scanId=$scanId"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("网络错误: ${e.message}")
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    val infoResponse = gson.fromJson(responseData, QRCodeInfoResponse::class.java)
                    if (infoResponse.code == 1 && infoResponse.data != null) {
                        listener.onSuccess(infoResponse.data)
                    } else {
                        listener.onFailure("获取扫码信息失败: ${infoResponse.msg}")
                    }
                } catch (e: Exception) {
                    listener.onFailure("解析扫码信息失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }

    // 扫码登录确认相关接口
    fun confirmQRCodeLogin(token: String, scanId: String, listener: OnQRCodeLoginListener) {
        val url = BASE_URL + "user/auth/qrcode/confirm?scanId=$scanId"
        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .addHeader("Authorization", token)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("网络错误: ${e.message}")
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        listener.onSuccess()
                    } else {
                        listener.onFailure("登录确认失败: ${response.code}")
                    }
                } catch (e: Exception) {
                    listener.onFailure("解析响应失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }
}
