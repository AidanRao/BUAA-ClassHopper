package top.aidanrao.buaa_classhopper.activity

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import top.aidanrao.buaa_classhopper.NavigationManager
import top.aidanrao.buaa_classhopper.R
import top.aidanrao.buaa_classhopper.data.model.dto.UserInfoDto
import top.aidanrao.buaa_classhopper.data.repository.CourseRepository
import top.aidanrao.buaa_classhopper.ui.CourseTableRenderer
import top.aidanrao.buaa_classhopper.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var tableLayout: TableLayout
    private lateinit var textViewDate: TextView
    private lateinit var datePickerContainer: RelativeLayout
    private lateinit var calendarIcon: ImageView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var userInfoTextView: TextView
    private lateinit var courseTableRenderer: CourseTableRenderer
    private lateinit var scanButton: ImageButton
    private lateinit var scanLauncher: ActivityResultLauncher<ScanOptions>
    
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var hamburgerButton: ImageButton

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        scanLauncher = registerForActivityResult(ScanContract()) { result ->
            val contents = result.contents
            if (contents.isNullOrEmpty()) {
                Toast.makeText(this, "未识别二维码", Toast.LENGTH_SHORT).show()
            } else {
                handleScanResult(contents)
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.content_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        initObservers()
        initDrawer()
        
        // 获取用户信息
        viewModel.fetchUserProfile()
        
        userInfoTextView.text = "当前 SSO 身份"

        // 设置默认日期
        val currentDate = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        textViewDate.text = dateFormat.format(currentDate.time)
        
        viewModel.getClassInfo(textViewDate.text.toString())
    }

    private fun initViews() {
        tableLayout = findViewById(R.id.tableLayout)
        textViewDate = findViewById(R.id.textViewDate)
        datePickerContainer = findViewById(R.id.datePickerContainer)
        calendarIcon = findViewById(R.id.calendarIcon)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        userInfoTextView = findViewById(R.id.userInfoTextView)
        hamburgerButton = findViewById(R.id.hamburger_button)
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        scanButton = findViewById(R.id.scanButton)

        courseTableRenderer = CourseTableRenderer(
            context = this,
            tableLayout = tableLayout,
            onSignClick = { courseId ->
                viewModel.signClass(
                    courseId,
                    textViewDate.text.toString()
                )
            }
        )

        datePickerContainer.setOnClickListener { showDatePickerDialog() }
        calendarIcon.setOnClickListener { showDatePickerDialog() }

        findViewById<Button>(R.id.btnGetClass).setOnClickListener {
            viewModel.getClassInfo(textViewDate.text.toString())
        }

        scanButton.setOnClickListener { startScan() }
    }

    /**
     * 初始化侧边栏
     */
    private fun initDrawer() {
        // 获取侧边栏容器
        val drawerContainer = findViewById<View>(R.id.drawer_container)

        // 汉堡按钮点击事件
        hamburgerButton.setOnClickListener {
            drawerContainer?.let { container ->
                drawerLayout.openDrawer(container)
            }
        }

        // 侧边栏菜单项点击事件
        navView.setNavigationItemSelectedListener {
            val container = findViewById<View>(R.id.drawer_container)
            when (it.itemId) {
                R.id.menu_home -> {
                    Toast.makeText(this, "首页", Toast.LENGTH_SHORT).show()
                    if (container != null) drawerLayout.closeDrawer(container)
                    true
                }
                R.id.menu_announcement -> {
                    NavigationManager.navigate(this, "/announcement")
                    if (container != null) drawerLayout.closeDrawer(container)
                    true
                }
                R.id.menu_lab -> {
                    NavigationManager.navigate(this, "/lab")
                    if (container != null) drawerLayout.closeDrawer(container)
                    true
                }
                R.id.menu_settings -> {
                    viewModel.fetchUserProfile()
                    NavigationManager.navigate(this, "/settings")
                    if (container != null) drawerLayout.closeDrawer(container)
                    true
                }
                R.id.menu_about -> {
                    NavigationManager.navigate(this, "/about")
                    if (container != null) drawerLayout.closeDrawer(container)
                    true
                }
                else -> {
                    false
                }
            }
        }
    }

    private fun initObservers() {
        viewModel.courses.observe(this) { courses ->
            hideEmptyState()
            courseTableRenderer.render(courses)
        }

        viewModel.userInfo.observe(this) { info ->
            userInfoTextView.text = info
        }

        viewModel.isEmpty.observe(this) { isEmpty ->
            if (isEmpty) showEmptyState() else hideEmptyState()
        }

        viewModel.error.observe(this) { errorMsg ->
            if (errorMsg == CourseRepository.VPN_SESSION_EXPIRED_MESSAGE ||
                errorMsg == CourseRepository.DIRECT_SESSION_EXPIRED_MESSAGE) {
                showSessionExpiredDialog(errorMsg)
            } else {
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.toastMessage.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        
        // 观察用户信息变化
        viewModel.userProfile.observe(this) { userInfo ->
            updateDrawerHeader(userInfo)
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
    }
    
    private fun showEmptyState() {
        tableLayout.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
    }

    private var vpnExpiredDialogShown = false
    private fun showSessionExpiredDialog(message: String) {
        if (vpnExpiredDialogShown) return
        vpnExpiredDialogShown = true
        AlertDialog.Builder(this)
            .setTitle("请登录 SSO")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("去登录") { dialog, _ ->
                dialog.dismiss()
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("取消", null)
            .setOnDismissListener { vpnExpiredDialogShown = false }
            .show()
    }

    private fun hideEmptyState() {
        tableLayout.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()

        try {
            val dateStr = textViewDate.text.toString()
            if (dateStr.isNotEmpty()) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = dateFormat.parse(dateStr)
                if (date != null) {
                    calendar.time = date
                }
            }
        } catch (_: Exception) { }
        
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDayOfMonth ->
            val formattedMonth = String.format(Locale.getDefault(), "%02d", selectedMonth + 1)
            val formattedDay = String.format(Locale.getDefault(), "%02d", selectedDayOfMonth)
            val formattedDate = "$selectedYear-$formattedMonth-$formattedDay"
            textViewDate.text = formattedDate
            
            // 自动加载
            viewModel.getClassInfo(formattedDate)
        }, year, month, day).show()
    }

    private fun startScan() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("请对准二维码")
            .setBeepEnabled(true)
            .setOrientationLocked(true)
            .setCaptureActivity(ScanCaptureActivity::class.java)
        scanLauncher.launch(options)
    }

    private fun handleScanResult(contents: String) {
        val success = NavigationManager.navigate(this, contents)
        if (!success) {
            Toast.makeText(this, "无法处理二维码内容", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDrawerHeader(userInfo: UserInfoDto) {
        val headerView = findViewById<View>(R.id.drawer_header) ?: return
        
        val avatarImage = headerView.findViewById<ImageView>(R.id.avatar_image)
        val studentIdText = headerView.findViewById<TextView>(R.id.student_id_text)
        val verifiedText = headerView.findViewById<TextView>(R.id.verified_text)
        
        studentIdText.text = userInfo.studentId
        
        verifiedText.text = if (userInfo.verified) "已认证" else "未认证 / 点击登录"
        verifiedText.setTextColor(
            if (userInfo.verified) {
                ContextCompat.getColor(this, android.R.color.holo_green_light)
            } else {
                ContextCompat.getColor(this, R.color.home_text_on_hero)
            }
        )
        
        if (!userInfo.avatar.isNullOrEmpty()) {
            try {
                Glide.with(this)
                    .load(userInfo.avatar)
                    .circleCrop()
                    .placeholder(R.drawable.ic_home_student)
                    .error(R.drawable.ic_home_student)
                    .into(avatarImage)
            } catch (e: Exception) {
                e.printStackTrace()
                avatarImage.setImageResource(R.drawable.ic_home_student)
            }
        } else {
            avatarImage.setImageResource(R.drawable.ic_home_student)
        }
    }

    override fun onResume() {
        super.onResume()
        navView.setCheckedItem(R.id.menu_home)
    }
}
