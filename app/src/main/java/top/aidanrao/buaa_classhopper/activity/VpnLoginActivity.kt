package top.aidanrao.buaa_classhopper.activity

import android.annotation.SuppressLint
import android.os.Bundle
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
import top.aidanrao.buaa_classhopper.data.vpn.VpnCookieJar
import top.aidanrao.buaa_classhopper.data.vpn.VpnEndpoints
import top.aidanrao.buaa_classhopper.data.vpn.VpnPreferences
import javax.inject.Inject

/** Interactive SSO only; iClass API login obtains a fresh loginName for each operation. */
@AndroidEntryPoint
class VpnLoginActivity : AppCompatActivity() {
    @Inject lateinit var networkSelector: IclassNetworkSelector
    @Inject lateinit var vpnPreferences: VpnPreferences
    @Inject lateinit var vpnCookieJar: VpnCookieJar
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
            if (routeReady && !clearing) {
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
                    vpnPreferences.setSessionReady(vpn, true)
                    Toast.makeText(this@VpnLoginActivity, "$modeLabel SSO 登录成功", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
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
