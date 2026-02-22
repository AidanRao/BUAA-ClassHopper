package com.example.hello.service

import android.content.Context
import android.util.Log
import com.example.hello.model.ApiResponse
import com.example.hello.model.Course
import com.example.hello.model.FallbackScheduleResponse
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
    // 配置项常量定义（与SettingsActivity保持一致）
    companion object {
        private const val PREFS_NAME = "course_checkin_settings"
        private const val KEY_FALLBACK_ENABLED = "fallback_enabled"
    }
    
    // 检查fallback开关是否启用
    private fun isFallbackEnabled(): Boolean {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(KEY_FALLBACK_ENABLED, false)
    }
    
    // 调用fallback API获取课程表
    private fun callFallbackCourseSchedule(dateStr: String, authToken: String, listener: OnCourseScheduleListener) {
        // 将日期格式从YYYYMMDD转换为YYYY-MM-DD以适应fallback API要求
        val formattedDate = if (dateStr.length == 8) {
            "${dateStr.take(4)}-${dateStr.substring(4, 6)}-${dateStr.substring(6, 8)}"
        } else {
            dateStr // 如果不是预期格式，保持原样
        }
        
        val fallbackUrl = "https://101.42.43.228/api/course/list".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("date", formattedDate)
            ?.build()?.toString() ?: run {
            listener.onFailure("构建fallback请求URL失败")
            return
        }
        
        val fallbackRequest = Request.Builder()
            .url(fallbackUrl)
            .addHeader("Authorization", authToken)
            .get()
            .build()
        
        client.newCall(fallbackRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("fallback接口调用失败: ${e.message}")
                e.printStackTrace()
            }
            
            override fun onResponse(call: Call, response: Response) {
                Log.i("api-fallback", response.toString())
                Log.i("api-fallback", response.body.toString())
                try {
                    val responseData = response.body?.string() ?: run {
                        listener.onFailure("fallback接口返回空数据")
                        return
                    }
                    
                    val fallbackResponse = gson.fromJson(responseData, FallbackScheduleResponse::class.java)
                    
                    if (fallbackResponse.code != 1) {
                        listener.onFailure("fallback接口返回错误: ${fallbackResponse.msg}")
                        return
                    }
                    
                    val fallbackCourses = fallbackResponse.data.result
                    if (fallbackCourses.isEmpty()) {
                        listener.onEmpty()
                        return
                    }
                    
                    // 转换FallbackCourse为Course
                    val courses = fallbackCourses.mapNotNull { fallbackCourse ->
                        try {
                            Course(
                                id = fallbackCourse.id,
                                courseId = fallbackCourse.courseId,
                                courseName = fallbackCourse.courseName,
                                courseType = fallbackCourse.courseType,
                                weekDay = fallbackCourse.weekDay,
                                courseNum = fallbackCourse.courseNum,
                                teacherName = fallbackCourse.teacherName,
                                classroomName = fallbackCourse.classroomName,
                                signStatus = if (fallbackCourse.signStatus == "已签到") 1 else 0,
                                classBeginTime = LocalDateTime.parse(fallbackCourse.classBeginTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                                classEndTime = LocalDateTime.parse(fallbackCourse.classEndTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            )
                        } catch (e: Exception) {
                            Log.e("iclass-api", "转换fallback课程数据失败: ${e.message}", e)
                            null
                        }
                    }
                    
                    if (courses.isEmpty()) {
                        listener.onEmpty()
                    } else {
                        listener.onSuccess(courses)
                    }
                    
                } catch (e: Exception) {
                    listener.onFailure("解析fallback数据失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }
    
    // 调用fallback API进行签到
    private fun callFallbackSignClass(courseId: Int, authToken: String, listener: OnSignListener) {
        val fallbackUrl = "https://101.42.43.228/api/course/sign".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("courseScheduleId", courseId.toString())
            ?.build()?.toString() ?: run {
            listener.onFailure("构建fallback签到请求URL失败")
            return
        }
        
        val fallbackRequest = Request.Builder()
            .url(fallbackUrl)
            .addHeader("Authorization", authToken)
            .post(FormBody.Builder().build()) // 空的表单体
            .build()
        
        client.newCall(fallbackRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("fallback签到接口调用失败: ${e.message}")
                e.printStackTrace()
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string() ?: run {
                        listener.onFailure("fallback签到接口返回空数据")
                        return
                    }
                    
                    val fallbackResponse = gson.fromJson(responseData, ApiResponse::class.java)
                    
                    if (fallbackResponse.code == 1) {
                        listener.onSuccess()
                    } else {
                        listener.onFailure("fallback签到失败: ${fallbackResponse.msg}")
                    }
                    
                } catch (e: Exception) {
                    listener.onFailure("解析fallback签到响应失败: ${e.message}")
                }
            }
        })
    }
    
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
                }
            }
        })
    }

    fun getCourseSchedule(userId: String, sessionId: String, dateStr: String, listener: OnCourseScheduleListener, authToken: String? = null) {

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
                // 主接口失败，尝试fallback
                if (isFallbackEnabled() && authToken != null) {
                    callFallbackCourseSchedule(dateStr, authToken, listener)
                } else {
                    listener.onFailure("获取课表失败: ${e.message}")
                }
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
                    // 解析失败，尝试fallback
                    if (isFallbackEnabled() && authToken != null) {
                        callFallbackCourseSchedule(dateStr, authToken, listener)
                    } else {
                        listener.onFailure("解析数据失败: ${e.message}")
                    }
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
                    ?.toString() ?: run {
                    // 主URL构建失败，尝试fallback
                    if (isFallbackEnabled() && authToken != null) {
                        callFallbackSignClass(courseId, authToken, listener)
                    } else {
                        listener.onFailure("构建签到请求URL失败")
                    }
                    return
                }

                Log.i("iclass-api", signUrl)

                val signRequest = Request.Builder()
                        .url(signUrl)
                        .post(formBody)
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .build()

                client.newCall(signRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        // 主接口失败，尝试fallback
                        if (isFallbackEnabled() && authToken != null) {
                            callFallbackSignClass(courseId, authToken, listener)
                        } else {
                            listener.onFailure("签到失败: ${e.message}")
                        }
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
                                    val errorMsg = "签到失败: ${signJson.get("msg")?.asString ?: "未知错误"}"
                                    // 主接口签到失败，尝试fallback
                                    if (isFallbackEnabled() && authToken != null) {
                                        callFallbackSignClass(courseId, authToken, listener)
                                    } else {
                                        listener.onFailure(errorMsg)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // 解析失败，尝试fallback
                            if (isFallbackEnabled() && authToken != null) {
                                callFallbackSignClass(courseId, authToken, listener)
                            } else {
                                listener.onFailure("解析响应失败: ${e.message}")
                            }
                        }
                    }
                })
            }

            override fun onFailure(error: String) {
                // 登录失败，尝试fallback
                if (isFallbackEnabled() && authToken != null) {
                    callFallbackSignClass(courseId, authToken, listener)
                } else {
                    listener.onFailure(error)
                }
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