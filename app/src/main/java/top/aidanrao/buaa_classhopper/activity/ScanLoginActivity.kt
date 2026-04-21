package top.aidanrao.buaa_classhopper.activity

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import top.aidanrao.buaa_classhopper.R
import dagger.hilt.android.AndroidEntryPoint
import top.aidanrao.buaa_classhopper.viewmodel.ScanLoginViewModel

@AndroidEntryPoint
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

    private val viewModel: ScanLoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_login)

        // 获取传递过来的 scanId 参数
        scanId = intent.getStringExtra("scanId") ?: run {
            Toast.makeText(this, "缺少必要参数", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        initObservers()
        loadScanInfo()
    }

    private fun initViews() {
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

        confirmButton.setOnClickListener {
            confirmLogin()
        }

        cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun initObservers() {
        viewModel.qrCodeInfo.observe(this) { info ->
            ipTextView.text = info.ipAddress ?: "-"
            osTextView.text = info.os ?: "-"
            browserTextView.text = info.browser ?: "-"
            deviceTextView.text = info.device ?: "-"
            requestTimeTextView.text = info.requestTime ?: "-"
            loadingView.visibility = View.GONE
            contentView.visibility = View.VISIBLE
        }

        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                loadingView.visibility = View.VISIBLE
                contentView.visibility = View.GONE
            }
        }

        viewModel.error.observe(this) { error ->
            statusTextView.text = "获取扫码信息失败"
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            loadingView.visibility = View.GONE
            contentView.visibility = View.VISIBLE
        }

        viewModel.confirmSuccess.observe(this) { success ->
            if (success) {
                statusTextView.text = "登录确认成功"
                Toast.makeText(this, "登录确认成功", Toast.LENGTH_SHORT).show()
                statusTextView.postDelayed({ finish() }, 1000)
            }
        }
    }

    private fun loadScanInfo() {
        viewModel.loadQRCodeInfo(scanId)
    }

    private fun confirmLogin() {
        viewModel.confirmLogin(scanId)
    }
}
