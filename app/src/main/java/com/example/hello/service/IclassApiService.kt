package com.example.hello.service

import android.content.Context
import android.util.Log
import com.example.hello.model.Course
import com.example.hello.model.ScheduleVO
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.*

class IclassApiService(private val context: Context) {
    // 创建信任所有证书的OkHttpClient
    private val client = OkHttpClient.Builder()
        .sslSocketFactory(createSSLSocketFactory(), object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        .hostnameVerifier { _, _ -> true }
        .build()
    
    // 创建支持LocalDateTime的Gson实例
    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeDeserializer())
        .create()
    
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

    interface OnLoginListener {
        fun onSuccess(userId: String, sessionId: String, realName: String, academyName: String)
        fun onFailure(error: String)
    }

    interface OnCourseScheduleListener {
        fun onSuccess(courses: List<Course>)
        fun onEmpty()
        fun onFailure(error: String)
    }

    interface OnSignListener {
        fun onSuccess()
        fun onFailure(error: String)
    }

    fun login(id: String, listener: OnLoginListener) {
        val loginUrl = "https://iclass.buaa.edu.cn:8346/app/user/login.action".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("password", "")
            ?.addQueryParameter("phone", id)
            ?.addQueryParameter("userLevel", "1")
            ?.addQueryParameter("verificationType", "2")
            ?.addQueryParameter("verificationUrl", "")
            ?.build()
            ?.toString() ?: return

        val loginRequest = Request.Builder()
            .url(loginUrl)
            .get()
            .build()

        client.newCall(loginRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("登录失败: ${e.message}")
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val loginData = response.body?.string()
                    val loginJson = gson.fromJson(loginData, JsonObject::class.java)
                    val resultObject = loginJson.getAsJsonObject("result")
                    val userId = resultObject.get("id").asString
                    val sessionId = resultObject.get("sessionId").asString
                    val realName = resultObject.get("realName").asString
                    val academyName = resultObject.get("academyName").asString
                    listener.onSuccess(userId, sessionId, realName, academyName)
                } catch (e: Exception) {
                    listener.onFailure("登录失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }

    fun getCourseSchedule(userId: String, sessionId: String, dateStr: String, listener: OnCourseScheduleListener) {

        val scheduleUrl = "https://iclass.buaa.edu.cn:8346/app/course/get_stu_course_sched.action".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("dateStr", dateStr)
            ?.addQueryParameter("id", userId)
            ?.build()
            ?.toString() ?: return

        val scheduleRequest = Request.Builder()
            .url(scheduleUrl)
            .addHeader("sessionId", sessionId)
            .get()
            .build()

        client.newCall(scheduleRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("获取课表失败: ${e.message}")
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val scheduleData = response.body?.string()
                Log.i("iclass-api", "get schedule $scheduleData")
                try {
                    scheduleData?.let {
                        val scheduleVO = gson.fromJson(it, ScheduleVO::class.java)
                        // 检查status是否为2，如果是则表示没有课程数据
                        if (scheduleVO.status == "2" || scheduleVO.result.isNullOrEmpty()) {
                            listener.onEmpty()
                        } else {
                            val courses = scheduleVO.result
                            if (courses.isNotEmpty()) {
                                listener.onSuccess(courses)
                            } else {
                                listener.onEmpty()
                            }
                        }
                    }
                } catch (e: Exception) {
                    listener.onFailure("解析数据失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }

    fun signClass(studentId: String, courseId: Int, listener: OnSignListener, authToken: String?) {
        // 首先登录获取sessionId
        login(studentId, object : OnLoginListener {
            override fun onSuccess(userId: String, sessionId: String, realName: String, academyName: String) {
                // 构建签到请求
                val timestamp = System.currentTimeMillis()
                val formBody = FormBody.Builder()
                    .add("id", userId)
                    .build()

                val signUrl = "http://iclass.buaa.edu.cn:8081/app/course/stu_scan_sign.action".toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("courseSchedId", courseId.toString())
                    ?.addQueryParameter("timestamp", timestamp.toString())
                    ?.build()
                    ?.toString() ?: return

                Log.i("iclass-api", signUrl)

                val signRequest = Request.Builder()
                        .url(signUrl)
                        .post(formBody)
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .build()

                client.newCall(signRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        listener.onFailure("签到失败: ${e.message}")
                        e.printStackTrace()
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val signData = response.body?.string()
                        Log.i("iclass-api", "sign result $signData")
                        try {
                            signData?.let {
                                val signJson = gson.fromJson(it, JsonObject::class.java)
                                if (signJson.has("result")) {
                                    listener.onSuccess()
                                } else {
                                    listener.onFailure("签到失败: ${signJson.get("msg")?.asString ?: "未知错误"}")
                                }
                            }
                        } catch (e: Exception) {
                            listener.onFailure("解析响应失败: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                })
            }

            override fun onFailure(error: String) {
                listener.onFailure(error)
            }
        })
    }
    
    // LocalDateTime反序列化器
    private class LocalDateTimeDeserializer : com.google.gson.JsonDeserializer<LocalDateTime> {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        
        override fun deserialize(json: com.google.gson.JsonElement?, typeOfT: java.lang.reflect.Type?, context: com.google.gson.JsonDeserializationContext?): LocalDateTime? {
            return json?.asString?.let {
                LocalDateTime.parse(it, formatter)
            }
        }
    }
}