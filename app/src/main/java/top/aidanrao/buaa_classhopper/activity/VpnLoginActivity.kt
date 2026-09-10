package top.aidanrao.buaa_classhopper.activity

import android.annotation.SuppressLint
import android.os.Bundle
import top.aidanrao.buaa_classhopper.data.model.IclassAccessException
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import top.aidanrao.buaa_classhopper.data.vpn.IclassNetworkSelector
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import top.aidanrao.buaa_classhopper.R
import top.aidanrao.buaa_classhopper.data.vpn.IclassSession
import top.aidanrao.buaa_classhopper.data.vpn.IclassSessionExpiredException
import top.aidanrao.buaa_classhopper.data.vpn.VpnCookieJar
import top.aidanrao.buaa_classhopper.data.vpn.VpnEndpoints
import top.aidanrao.buaa_classhopper.data.vpn.VpnPreferences
import top.aidanrao.buaa_classhopper.data.repository.CourseRepository
import top.aidanrao.buaa_classhopper.data.model.Result
import javax.inject.Inject

/** Hand the trusted CAS callback to API login before reporting authentication success. */
@AndroidEntryPoint
class VpnLoginActivity : AppCompatActivity() {
    @Inject lateinit var networkSelector: IclassNetworkSelector
    @Inject lateinit var vpnPreferences: VpnPreferences
    @Inject lateinit var vpnCookieJar: VpnCookieJar
    @Inject lateinit var courseRepository: CourseRepository
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private var loginHandled = false
    private var clearing = false
    private var portalForwarded = false
    private var vpn = false
    private var routeReady = false
    private val modeLabel get() = if (vpn) "VPN" else "直连"
    private val entryUrl get() = if (vpn) VpnEndpoints.VPN_CAS_LOGIN_URL else IclassSession.jumpUrl(false)

    @SuppressLint("SetJavaScriptEnabled", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vpn_login)
        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)
        statusText = findViewById(R.id.status_text)
        findViewById<TextView>(R.id.login_title).text = "iClass SSO 登录"
        statusText.text = "正在检测校园网访问路径…"
        findViewById<ImageView>(R.id.back_button).setOnClickListener { finish() }
        findViewById<Button>(R.id.clear_button).setOnClickListener {
            if (routeReady && !clearing && !loginHandled) {
                clearing = true
                loginHandled = false
                portalForwarded = false
                webView.stopLoading()
                webView.clearHistory()
                vpnCookieJar.clear(vpn) {
                    if (!isFinishing && !isDestroyed) {
                        clearing = false
                        webView.loadUrl(entryUrl)
                    }
                }
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (clearing || loginHandled) return
                val current = url?.toHttpUrlOrNull() ?: return
                if (IclassSession.isLanding(current, IclassSession.baseUrl(vpn)) &&
                    IclassSession.loginName(current) != null) {
                    loginHandled = true
                    CookieManager.getInstance().flush()
                    if (vpn) vpnCookieJar.persistCookies()
                    statusText.text = "网页认证完成，正在验证$modeLabel iClass 会话…"
                    lifecycleScope.launch {
                        // Reopening the jump page can omit the token after CAS has authenticated.
                        // Validate this callback token with the API, then retain it for later logins.
                        when (val result = courseRepository.login(vpn, IclassSession.loginName(current))) {
                            is Result.Success -> {
                                Toast.makeText(this@VpnLoginActivity, "$modeLabel SSO 登录成功", Toast.LENGTH_SHORT).show()
                                setResult(RESULT_OK)
                                finish()
                            }
                            is Result.Error -> {
                                if (result.exception is IclassAccessException) {
                                    statusText.text = "SSO 认证成功；${result.getErrorMessage()}"
                                    // Keep the validated SSO session; do not loop through CAS again.
                                    return@launch
                                }
                                val expired = result.exception as? IclassSessionExpiredException
                                if (expired != null) {
                                    Log.w("VpnLoginActivity", "iClass session rejected: ${expired.diagnostic ?: "missing loginName"}")
                                }
                                vpnPreferences.setSessionReady(vpn, false)
                                statusText.text = "网页认证已完成，但应用会话验证失败：${result.getErrorMessage()}"
                                loginHandled = false
                            }
                            Result.Loading -> { loginHandled = false }
                        }
                    }
                } else if (vpn && !portalForwarded && IclassSession.isVpnPortal(current)) {
                    portalForwarded = true
                    // The VPN portal alone does not establish an iClass session.
                    webView.loadUrl(IclassSession.jumpUrl(true))
                } else {
                    statusText.text = "请完成$modeLabel SSO 登录；操作将使用此账号身份"
                }
            }
        }
        lifecycleScope.launch {
            vpn = networkSelector.useVpn()
            routeReady = true
            findViewById<TextView>(R.id.login_title).text = "$modeLabel SSO 登录"
            statusText.text = "已自动选择$modeLabel，请完成北航统一身份认证"
            webView.loadUrl(entryUrl)
        }
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
