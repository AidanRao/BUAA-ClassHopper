package com.example.hello.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.hello.NavigationManager
import com.example.hello.R
import com.example.hello.service.ApiService
import com.example.hello.utils.DeviceIdUtil
import com.example.hello.utils.SignUtils

class SplashActivity : AppCompatActivity() {
    private lateinit var splashImage: ImageView
    private lateinit var apiService: ApiService
    private var splashData: SplashResponseData? = null
    private val TAG = "SplashActivity"
    private var splashHandler: Handler? = null

    private val BASE_URL = "http://10.0.2.2:8088/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        splashImage = findViewById(R.id.splash_image)
        apiService = ApiService(this)

        // 获取开屏广告数据
        fetchSplashData()

        // 设置图片点击事件
        splashImage.setOnClickListener {
            handleSplashClick()
        }
    }

    // 获取开屏广告数据
    private fun fetchSplashData() {
        val appKey = "buaa-classhopper-android"
        val appUUID = DeviceIdUtil.getPersistentUUID(this)

        // 构建请求参数
        val params = mapOf(
            "platform" to appKey,
            "deviceId" to appUUID
        )

        // 发送请求获取开屏广告数据
        val url = BASE_URL + "message/splash/fetch?platform=$appKey&deviceId=$appUUID"
        val request = okhttp3.Request.Builder()
            .url(url)
            .get()
            .build()

        apiService.client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "获取开屏广告失败: ${e.message}")
                runOnUiThread {
                    // 如果获取失败，直接跳转到主页面
                    navigateToMain()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    val responseData = response.body?.string()
                    Log.d(TAG, "开屏广告响应: $responseData")

                    // 解析响应数据
                    val gson = com.google.gson.Gson()
                    val splashResponse = gson.fromJson(responseData, SplashResponse::class.java)

                    if (splashResponse.code == 1 && splashResponse.data != null) {
                        splashData = splashResponse.data
                        Log.i("splash-api", "" + splashResponse.data)
                        runOnUiThread {
                            // 显示开屏广告图片
                            showSplashImage(splashData!!)
                        }
                    } else {
                        runOnUiThread {
                            navigateToMain()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析开屏广告响应失败: ${e.message}")
                    runOnUiThread {
                        navigateToMain()
                    }
                }
            }
        })
    }

    // 显示开屏广告图片
    private fun showSplashImage(data: SplashResponseData) {
        // 使用Glide加载图片
        Glide.with(this)
            .load(data.resourceUrl)
            .fitCenter()
            .into(splashImage)

        // 设置广告显示时长
        val duration = try {
            data.duration.toLong()
        } catch (e: NumberFormatException) {
            3000L // 默认3秒
        }

        // 定时器结束后跳转到主页面
        splashHandler = Handler(Looper.getMainLooper())
        splashHandler?.postDelayed({
            navigateToMain()
        }, duration)
    }

    // 处理开屏广告点击事件
    private fun handleSplashClick() {
        // 取消定时器，防止点击后仍跳转到主页面
        splashHandler?.removeCallbacksAndMessages(null)
        
        splashData?.let {
            when (it.clickAction) {
                "JUMP_URL" -> {
                    // 处理跳转逻辑
                    if (it.clickUrl.isNotEmpty()) {
                        NavigationManager.navigate(this, it.clickUrl)
                        // 跳转到目标页面后，关闭当前SplashActivity
                        finish()
                    }
                }
                "NONE" -> {
                    // 什么都不做，直接跳转到主页面
                    navigateToMain()
                }
                else -> {
                    navigateToMain()
                }
            }
        } ?: run {
            navigateToMain()
        }
    }

    // 跳转到主页面
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    // 开屏广告响应数据模型
    data class SplashResponse(
        val code: Int,
        val msg: String,
        val data: SplashResponseData?
    )

    data class SplashResponseData(
        val id: String,
        val resourceUrl: String,
        val clickAction: String,
        val clickUrl: String,
        val duration: String
    )
}
