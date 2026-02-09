package com.example.hello.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.hello.R
import com.example.hello.service.ApiService
import com.example.hello.viewmodel.MainViewModel

class SettingsActivity : AppCompatActivity() {
    // 配置项常量定义（用于SharedPreferences存储）
    companion object {
        private const val PREFS_NAME = "course_checkin_settings"
        private const val KEY_FALLBACK_ENABLED = "fallback_enabled"
        // 后续可以在这里添加更多配置项常量
    }

    private lateinit var backButton: ImageView
    private lateinit var studentIdText: TextView
    private lateinit var verifiedStatusText: TextView
    private lateinit var authButton: Button
    private lateinit var infoPersonalButton: ImageButton
    private lateinit var infoCourseButton: ImageButton
    private lateinit var fallbackSwitch: Switch
    private lateinit var fallbackDescription: TextView
    private lateinit var viewModel: MainViewModel
    private lateinit var sharedPreferences: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // 初始化ViewModel，使用与MainActivity相同的实例
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        
        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        initViews()
        initListeners()
        initObservers()
    }

    // 保存Fallback实现开关状态到SharedPreferences
    private fun saveFallbackEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_FALLBACK_ENABLED, enabled).apply()
    }

    // 从SharedPreferences读取Fallback实现开关状态
    private fun isFallbackEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_FALLBACK_ENABLED, false) // 默认关闭
    }



    private fun initViews() {
        backButton = findViewById(R.id.back_button)
        studentIdText = findViewById(R.id.student_id_text)
        verifiedStatusText = findViewById(R.id.verified_status_text)
        authButton = findViewById(R.id.auth_button)
        infoPersonalButton = findViewById(R.id.info_personal)
        infoCourseButton = findViewById(R.id.info_course)
        fallbackSwitch = findViewById(R.id.fallback_switch)
        fallbackDescription = findViewById(R.id.fallback_description)
    }

    private fun initListeners() {
        // 返回按钮点击事件
        backButton.setOnClickListener {
            finish()
        }

        // 身份验证按钮点击事件
        authButton.setOnClickListener {
            // 跳转到身份验证界面
            val intent = Intent(this, VerificationActivity::class.java)
            startActivity(intent)
        }

        // 个人信息配置说明点击事件
        infoPersonalButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("个人信息配置说明")
                .setMessage("个人信息配置用于验证您的北航身份，包括学号和身份认证状态。通过北航邮箱进行验证码验证后，您将获得完整的应用功能权限。")
                .setPositiveButton("确定", null)
                .show()
        }

        // 课程打卡配置说明点击事件
        infoCourseButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("课程打卡配置说明")
                .setMessage("课程打卡配置用于设置和管理APP获取课表和打卡的实现方式。")
                .setPositiveButton("确定", null)
                .show()
        }

        // Fallback实现开关点击事件
        fallbackSwitch.setOnCheckedChangeListener { _, isChecked ->
            // 检查用户是否已认证
            val isVerified = viewModel.userProfile.value?.verified ?: false
            if (isChecked && !isVerified) {
                // 未认证用户尝试开启，显示提示并重置开关状态
                AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("您需要先完成北航身份认证才能开启该功能")
                    .setPositiveButton("确定") { _, _ ->
                        // 重置开关状态
                        fallbackSwitch.isChecked = false
                    }
                    .show()
            } else {
                // 已认证用户或关闭操作，保存状态
                saveFallbackEnabled(isChecked)
            }
        }
    }

    private fun initObservers() {
        // 观察用户信息变化
        viewModel.userProfile.observe(this) {
            updateUserInfo(it)
        }
    }

    private fun updateUserInfo(userInfo: ApiService.UserInfoResponse.UserInfoData) {
        // 更新学号显示
        studentIdText.text = userInfo.studentId

        // 更新认证状态
        verifiedStatusText.text = if (userInfo.verified) "已认证" else "未认证"
        verifiedStatusText.setTextColor(
            if (userInfo.verified) 
                resources.getColor(android.R.color.holo_green_dark) 
            else 
                resources.getColor(android.R.color.darker_gray)
        )

        // 根据认证状态更新按钮文本
        authButton.text = if (userInfo.verified) "重新验证" else "进行北航身份验证"
        
        // 根据认证状态更新Fallback开关的可用性
        updateFallbackSwitchAvailability(userInfo.verified)
    }

    // 根据用户认证状态更新Fallback开关的可用性
    private fun updateFallbackSwitchAvailability(isVerified: Boolean) {
        // 只有在用户未认证时才强制关闭开关并保存状态
        if (!isVerified && fallbackSwitch.isChecked) {
            fallbackSwitch.isChecked = false
            saveFallbackEnabled(false)
        }
        // 当用户已认证时，保持开关当前状态（从SharedPreferences加载的状态）
        // 这样可以避免fetchUserProfile()完成后覆盖已保存的设置
        
        // 更新开关的可点击状态
        fallbackSwitch.isClickable = true // 始终允许点击，在点击事件中检查认证状态
    }

    override fun onResume() {
        super.onResume()
        
        // 先加载保存的Fallback实现开关状态，但要暂时移除监听器防止触发
        val isEnabled = isFallbackEnabled()
        fallbackSwitch.setOnCheckedChangeListener(null) // 暂时移除监听器
        fallbackSwitch.isChecked = isEnabled
        fallbackSwitch.setOnCheckedChangeListener { _, isChecked ->
            // 检查用户是否已认证
            val isVerified = viewModel.userProfile.value?.verified ?: false
            if (isChecked && !isVerified) {
                // 未认证用户尝试开启，显示提示并重置开关状态
                AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("您需要先完成北航身份认证才能开启该功能")
                    .setPositiveButton("确定") { _, _ ->
                        // 重置开关状态
                        fallbackSwitch.isChecked = false
                    }
                    .show()
            } else {
                // 已认证用户或关闭操作，保存状态
                saveFallbackEnabled(isChecked)
            }
        } // 重新添加监听器
        
        // 只在有用户信息时才更新开关可用性，否则等fetchUserProfile完成后由观察者更新
        if (viewModel.userProfile.value != null) {
            val isVerified = viewModel.userProfile.value?.verified ?: false
            updateFallbackSwitchAvailability(isVerified)
        }
        
        // 最后再重新获取用户信息，确保显示最新状态
        viewModel.fetchUserProfile()
    }
}