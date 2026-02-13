package com.example.hello.activity

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.bumptech.glide.Glide
import com.example.hello.ui.CourseTableRenderer
import com.example.hello.ui.WebSocketStatusIndicator
import com.example.hello.viewmodel.MainViewModel
import com.google.android.material.navigation.NavigationView
import com.example.hello.R
import com.example.hello.NavigationManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var tableLayout: TableLayout
    private lateinit var editTextId: EditText
    private lateinit var textViewDate: TextView
    private lateinit var datePickerContainer: RelativeLayout
    private lateinit var calendarIcon: ImageView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var userInfoTextView: TextView
    private lateinit var webSocketStatusIcon: ImageView
    private lateinit var webSocketStatusIndicator: WebSocketStatusIndicator
    private lateinit var courseTableRenderer: CourseTableRenderer
    private lateinit var scanButton: ImageButton
    private lateinit var scanLauncher: ActivityResultLauncher<ScanOptions>
    
    // 侧边栏相关变量
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var hamburgerButton: ImageButton

    private val viewModel: MainViewModel by viewModels()

    private val PREFS_NAME = "ClassHopperPrefs"
    private val KEY_STUDENT_ID = "student_id"

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
        
        // 恢复保存的学号
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedStudentId = sharedPreferences.getString(KEY_STUDENT_ID, "22370000")
        editTextId.setText(savedStudentId)
        
        // 设置默认日期
        val currentDate = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        textViewDate.text = dateFormat.format(currentDate.time)
    }

    private fun initViews() {
        tableLayout = findViewById(R.id.tableLayout)
        editTextId = findViewById(R.id.editTextId)
        textViewDate = findViewById(R.id.textViewDate)
        datePickerContainer = findViewById(R.id.datePickerContainer)
        calendarIcon = findViewById(R.id.calendarIcon)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        userInfoTextView = findViewById(R.id.userInfoTextView)
        webSocketStatusIcon = findViewById(R.id.webSocketStatusIcon)
        hamburgerButton = findViewById(R.id.hamburger_button)
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        scanButton = findViewById(R.id.scanButton)

        webSocketStatusIndicator = WebSocketStatusIndicator(this, webSocketStatusIcon)
        
        courseTableRenderer = CourseTableRenderer(
            context = this,
            tableLayout = tableLayout,
            onSignClick = { courseId -> 
                viewModel.signClass(
                    editTextId.text.toString(), 
                    courseId, 
                    textViewDate.text.toString()
                ) 
            }
        )

        datePickerContainer.setOnClickListener { showDatePickerDialog() }
        calendarIcon.setOnClickListener { showDatePickerDialog() }

        findViewById<Button>(R.id.btnGetClass).setOnClickListener {
            val id = editTextId.text.toString()
            val date = textViewDate.text.toString()
            viewModel.getClassInfo(id, date)
            
            // 保存学号
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                putString(KEY_STUDENT_ID, id)
            }
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
                    // 处理首页点击
                    Toast.makeText(this, "首页", Toast.LENGTH_SHORT).show()
                    if (container != null) drawerLayout.closeDrawer(container)
                    true
                }
                R.id.menu_announcement -> {
                    // 处理公告点击，跳转到公告页面
                    NavigationManager.navigate(this, "/announcement")
                    if (container != null) drawerLayout.closeDrawer(container)
                    true
                }
                R.id.menu_settings -> {
                    // 处理设置点击
                    // 获取最新用户信息
                    viewModel.fetchUserProfile()
                    NavigationManager.navigate(this, "/settings")
                    if (container != null) drawerLayout.closeDrawer(container)
                    true
                }
                R.id.menu_about -> {
                    // 处理关于点击
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
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        }

        viewModel.toastMessage.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        viewModel.webSocketStatus.observe(this) { status ->
            when (status) {
                MainViewModel.WebSocketStatus.CONNECTED -> webSocketStatusIndicator.showConnected()
                MainViewModel.WebSocketStatus.CONNECTING -> webSocketStatusIndicator.showConnecting()
                MainViewModel.WebSocketStatus.DISCONNECTED -> webSocketStatusIndicator.showDisconnected()
                else -> webSocketStatusIndicator.showDisconnected()
            }
        }
        
        // 观察用户信息变化
        viewModel.userProfile.observe(this) { userInfo ->
            updateDrawerHeader(userInfo)
        }
    }
    
    override fun onPause() {
        super.onPause()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putString(KEY_STUDENT_ID, editTextId.text.toString())
        }
    }

    private fun showEmptyState() {
        tableLayout.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
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
            val id = editTextId.text.toString()
            if (id.isNotEmpty()) {
                viewModel.getClassInfo(id, formattedDate)
            }
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
        val appBase = "https://classhopper.com/app/"
        val targetPath = if (contents.startsWith(appBase)) {
            val raw = contents.removePrefix(appBase)
            val normalized = if (raw.startsWith("/")) raw else "/$raw"
            if (normalized == "/") "/main" else normalized
        } else {
            contents
        }
        val success = NavigationManager.navigate(this, targetPath)
        if (!success) {
            Toast.makeText(this, "无法处理二维码内容", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 更新侧边栏用户信息（现在位于底部）
     */
    private fun updateDrawerHeader(userInfo: com.example.hello.service.ApiService.UserInfoResponse.UserInfoData) {
        // 获取底部的用户信息 View
        val footerView = findViewById<View>(R.id.drawer_footer) ?: return
        
        val avatarImage = footerView.findViewById<ImageView>(R.id.avatar_image)
        val studentIdText = footerView.findViewById<TextView>(R.id.student_id_text)
        val verifiedText = footerView.findViewById<TextView>(R.id.verified_text)
        
        // 设置学生ID
        studentIdText.text = userInfo.studentId
        
        // 设置认证状态
        verifiedText.text = if (userInfo.verified) "已认证" else "未认证"
        verifiedText.setTextColor(if (userInfo.verified) resources.getColor(android.R.color.holo_green_dark) else resources.getColor(android.R.color.darker_gray))
        
        // 加载头像
        if (!userInfo.avatar.isNullOrEmpty()) {
            // 使用Glide或其他图片加载库加载头像
            try {
                Glide.with(this)
                    .load(userInfo.avatar)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(avatarImage)
            } catch (e: Exception) {
                e.printStackTrace()
                // 如果Glide出现异常，使用默认头像
                avatarImage.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            // 使用默认头像
            avatarImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

}
