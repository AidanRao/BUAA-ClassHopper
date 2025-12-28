package com.example.hello

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
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
import com.google.android.material.navigation.NavigationView
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
    }

    /**
     * 初始化侧边栏
     */
    private fun initDrawer() {
        // 汉堡按钮点击事件
        hamburgerButton.setOnClickListener {
            drawerLayout.openDrawer(navView)
        }

        // 侧边栏菜单项点击事件
        navView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.menu_home -> {
                    // 处理首页点击
                    Toast.makeText(this, "首页", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawer(navView)
                    true
                }
                R.id.menu_announcement -> {
                    // 处理公告点击，跳转到公告页面
                    val intent = android.content.Intent(this, AnnouncementActivity::class.java)
                    startActivity(intent)
                    drawerLayout.closeDrawer(navView)
                    true
                }
                R.id.menu_settings -> {
                    // 处理设置点击
                    Toast.makeText(this, "设置", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawer(navView)
                    true
                }
                R.id.menu_about -> {
                    // 处理关于点击
                    Toast.makeText(this, "关于", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawer(navView)
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
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

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

    /**
     * 更新侧边栏头部用户信息
     */
    private fun updateDrawerHeader(userInfo: com.example.hello.service.ApiService.UserInfoResponse.UserInfoData) {
        val headerView = navView.getHeaderView(0)
        val avatarImage = headerView.findViewById<ImageView>(R.id.avatar_image)
        val studentIdText = headerView.findViewById<TextView>(R.id.student_id_text)
        val verifiedText = headerView.findViewById<TextView>(R.id.verified_text)
        
        // 设置学生ID
        studentIdText.text = userInfo.studentId
        
        // 设置认证状态
        verifiedText.text = if (userInfo.verified) "已认证" else "未认证"
        verifiedText.setTextColor(if (userInfo.verified) resources.getColor(android.R.color.holo_green_dark) else resources.getColor(android.R.color.darker_gray))
        
        // 加载头像
        if (!userInfo.avatar.isNullOrEmpty()) {
            // 使用Glide或其他图片加载库加载头像
            // 这里需要确保项目中已添加Glide依赖
            try {
                val request = Glide.with(this)
                    .load(userInfo.avatar)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(avatarImage)
            } catch (e: Exception) {
                e.printStackTrace()
                // 如果Glide未配置，使用默认头像
                avatarImage.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            // 使用默认头像
            avatarImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

}