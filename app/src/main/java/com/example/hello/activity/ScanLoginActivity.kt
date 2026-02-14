package com.example.hello.activity

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.hello.R
import com.example.hello.service.ApiService

class ScanLoginActivity : AppCompatActivity() {
    private lateinit var scanId: String
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button
    private lateinit var loadingView: View
    private lateinit var contentView: View
    private lateinit var statusTextView: TextView
    private lateinit var ipTextView: TextView
    private lateinit var osTextView: TextView
    private lateinit var browserTextView: TextView
    private lateinit var deviceTextView: TextView
    private lateinit var requestTimeTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_login)

        // 获取传递过来的 scanId 参数
        scanId = intent.getStringExtra("scanId") ?: run {
            Toast.makeText(this, "缺少必要参数", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 初始化视图
        confirmButton = findViewById(R.id.confirm_button)
        cancelButton = findViewById(R.id.cancel_button)
        loadingView = findViewById(R.id.loading_view)
        contentView = findViewById(R.id.content_view)
        statusTextView = findViewById(R.id.status_text_view)
        ipTextView = findViewById(R.id.ip_text_view)
        osTextView = findViewById(R.id.os_text_view)
        browserTextView = findViewById(R.id.browser_text_view)
        deviceTextView = findViewById(R.id.device_text_view)
        requestTimeTextView = findViewById(R.id.request_time_text_view)

        // 设置按钮点击事件
        confirmButton.setOnClickListener {
            confirmLogin()
        }

        cancelButton.setOnClickListener {
            finish()
        }

        loadScanInfo()
    }

    private fun loadScanInfo() {
        loadingView.visibility = View.VISIBLE
        contentView.visibility = View.GONE

        val apiService = ApiService(this)
        apiService.getQRCodeInfo(scanId, object : ApiService.OnQRCodeInfoListener {
            override fun onSuccess(info: ApiService.QRCodeInfoData) {
                runOnUiThread {
                    ipTextView.text = info.ipAddress ?: "-"
                    osTextView.text = info.os ?: "-"
                    browserTextView.text = info.browser ?: "-"
                    deviceTextView.text = info.device ?: "-"
                    requestTimeTextView.text = info.requestTime ?: "-"
                    loadingView.visibility = View.GONE
                    contentView.visibility = View.VISIBLE
                }
            }

            override fun onFailure(error: String) {
                runOnUiThread {
                    statusTextView.text = "获取扫码信息失败"
                    Toast.makeText(this@ScanLoginActivity, error, Toast.LENGTH_SHORT).show()
                    loadingView.visibility = View.GONE
                    contentView.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun confirmLogin() {
        // 显示加载状态
        loadingView.visibility = View.VISIBLE
        contentView.visibility = View.GONE

        // 创建 ApiService 实例
        val apiService = ApiService(this)

        // 调用扫码登录确认接口
        apiService.getValidToken(object : ApiService.OnAuthListener {
            override fun onSuccess(token: String, expireAt: Long) {
                apiService.confirmQRCodeLogin(token, scanId, object : ApiService.OnQRCodeLoginListener {
                    override fun onSuccess() {
                        runOnUiThread {
                            statusTextView.text = "登录确认成功"
                            Toast.makeText(this@ScanLoginActivity, "登录确认成功", Toast.LENGTH_SHORT).show()
                            statusTextView.postDelayed({ finish() }, 1000)
                        }
                    }

                    override fun onFailure(error: String) {
                        runOnUiThread {
                            statusTextView.text = "登录确认失败"
                            Toast.makeText(this@ScanLoginActivity, error, Toast.LENGTH_SHORT).show()
                            loadingView.visibility = View.GONE
                            contentView.visibility = View.VISIBLE
                        }
                    }
                })
            }

            override fun onFailure(error: String) {
                runOnUiThread {
                    statusTextView.text = "登录确认失败"
                    Toast.makeText(this@ScanLoginActivity, error, Toast.LENGTH_SHORT).show()
                    loadingView.visibility = View.GONE
                    contentView.visibility = View.VISIBLE
                }
            }
        })
    }
}
