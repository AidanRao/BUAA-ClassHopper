package top.aidanrao.buaa_classhopper.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import top.aidanrao.buaa_classhopper.NavigationManager
import top.aidanrao.buaa_classhopper.R
import top.aidanrao.buaa_classhopper.utils.DeviceIdUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.ceil
import kotlin.math.max

class SplashActivity : AppCompatActivity() {
    private lateinit var splashImage: ImageView
    private lateinit var skipText: TextView
    private var splashData: SplashResponseData? = null
    private val TAG = "SplashActivity"
    private var splashHandler: Handler? = null
    private var skipRemainingSeconds = 0
    
    private val skipCountdownRunnable = object : Runnable {
        override fun run() {
            if (isNavigated.get()) {
                return
            }
            if (skipRemainingSeconds <= 0) {
                return
            }
            skipText.text = getString(R.string.splash_skip_countdown, skipRemainingSeconds)
            skipRemainingSeconds -= 1
            splashHandler?.postDelayed(this, 1000L)
        }
    }
    
    private val isNavigated = AtomicBoolean(false)
    private val FETCH_TIMEOUT = 2000L 
    
    private val BASE_URL = "http://39.105.96.112/api/"

    private val fetchTimeoutRunnable = Runnable {
        Log.w(TAG, "获取开屏广告超时，直接跳转主页")
        navigateToMain()
    }

    private val client: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        splashImage = findViewById(R.id.splash_image)
        skipText = findViewById(R.id.skip_text)
        
        splashHandler = Handler(Looper.getMainLooper())

        // 设置获取数据的超时处理
        splashHandler?.postDelayed(fetchTimeoutRunnable, FETCH_TIMEOUT)

        // 获取开屏广告数据
        fetchSplashData()

        splashImage.setOnClickListener {
            handleSplashClick()
        }

        skipText.setOnClickListener {
            navigateToMain()
        }
    }

    private fun fetchSplashData() {
        val appKey = "buaa-classhopper-android"
        val appUUID = DeviceIdUtil.getPersistentUUID(this)

        val url = BASE_URL + "message/splash/fetch?platform=$appKey&deviceId=$appUUID"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
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

                    val gson = com.google.gson.Gson()
                    val splashResponse = gson.fromJson(responseData, SplashResponse::class.java)

                    if (splashResponse.code == 1 && splashResponse.data != null) {
                        splashData = splashResponse.data
                        runOnUiThread {
                            // 成功获取数据，取消超时检测
                            splashHandler?.removeCallbacks(fetchTimeoutRunnable)

                            // 如果尚未跳转（即未超时），则显示开屏广告
                            if (!isNavigated.get()) {
                                showSplashImage(splashData!!)
                            }
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

    private fun showSplashImage(data: SplashResponseData) {
        Glide.with(this)
            .load(data.resourceUrl)
            .fitCenter()
            .into(splashImage)

        val duration = try {
            data.duration.toLong()
        } catch (e: NumberFormatException) {
            3000L
        }

        splashHandler?.removeCallbacks(skipCountdownRunnable)
        val totalSeconds = max(1, ceil(duration / 1000.0).toInt())
        skipRemainingSeconds = totalSeconds
        skipText.text = getString(R.string.splash_skip_countdown, skipRemainingSeconds)
        skipRemainingSeconds -= 1
        splashHandler?.postDelayed(skipCountdownRunnable, 1000L)

        splashHandler?.postDelayed({
            navigateToMain()
        }, duration)
    }

    private fun handleSplashClick() {
        splashHandler?.removeCallbacksAndMessages(null)
        
        splashData?.let {
            when (it.clickAction) {
                "JUMP_URL" -> {
                    if (it.clickUrl.isNotEmpty()) {
                        NavigationManager.navigate(this, it.clickUrl)
                        finish()
                    }
                }
                "NONE" -> {
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

    private fun navigateToMain() {
        if (isNavigated.compareAndSet(false, true)) {
            splashHandler?.removeCallbacksAndMessages(null)
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        splashHandler?.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

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
