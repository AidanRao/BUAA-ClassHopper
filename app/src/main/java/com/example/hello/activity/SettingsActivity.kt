package com.example.hello.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import com.example.hello.R
import com.example.hello.data.model.dto.UserInfoDto
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.content.edit
import com.example.hello.viewmodel.MainViewModel

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    // 配置项常量定义（用于SharedPreferences存储）
    companion object {
        private const val PREFS_NAME = "course_checkin_settings"
        private const val KEY_FALLBACK_ENABLED = "fallback_enabled"
    }

    private lateinit var backButton: ImageView
    private lateinit var studentIdText: TextView
    private lateinit var verifiedStatusText: TextView
    private lateinit var authButton: Button
    private lateinit var infoPersonalButton: ImageButton
    private lateinit var infoCourseButton: ImageButton
    private lateinit var fallbackSwitch: Switch
    private lateinit var fallbackDescription: TextView
    private lateinit var sharedPreferences: android.content.SharedPreferences
    
    private val viewModel: MainViewModel by viewModels()
    
    private val fallbackSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        val isVerified = viewModel.userProfile.value?.verified ?: false
        if (isChecked && !isVerified) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("您需要先完成北航身份认证才能开启该功能")
                .setPositiveButton("确定") { _, _ ->
                    fallbackSwitch.isChecked = false
                }
                .show()
        } else {
            saveFallbackEnabled(isChecked)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        initViews()
        initListeners()
        initObservers()
    }

    // 保存Fallback实现开关状态到SharedPreferences
    private fun saveFallbackEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_FALLBACK_ENABLED, enabled) }
    }

    // 从SharedPreferences读取Fallback实现开关状态
    private fun isFallbackEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_FALLBACK_ENABLED, false)
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
        backButton.setOnClickListener {
            finish()
        }

        // 身份验证按钮点击事件
        authButton.setOnClickListener {
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
        fallbackSwitch.setOnCheckedChangeListener(fallbackSwitchListener)
    }

    private fun initObservers() {
        viewModel.userProfile.observe(this) {
            updateUserInfo(it)
        }
    }

    private fun updateUserInfo(userInfo: UserInfoDto) {
        studentIdText.text = userInfo.studentId

        verifiedStatusText.text = if (userInfo.verified) "已认证" else "未认证"
        verifiedStatusText.setTextColor(
            if (userInfo.verified) 
                resources.getColor(android.R.color.holo_green_dark) 
            else 
                resources.getColor(android.R.color.darker_gray)
        )

        authButton.text = if (userInfo.verified) "重新验证" else "进行北航身份验证"
        
        updateFallbackSwitchAvailability(userInfo.verified)
    }

    private fun updateFallbackSwitchAvailability(isVerified: Boolean) {
        if (!isVerified && fallbackSwitch.isChecked) {
            fallbackSwitch.isChecked = false
            saveFallbackEnabled(false)
        }
        
        fallbackSwitch.isClickable = true
    }

    override fun onResume() {
        super.onResume()
        
        // 先加载保存的Fallback实现开关状态，但要暂时移除监听器防止触发
        val isEnabled = isFallbackEnabled()
        fallbackSwitch.setOnCheckedChangeListener(null)
        fallbackSwitch.isChecked = isEnabled
        fallbackSwitch.setOnCheckedChangeListener(fallbackSwitchListener)
        
        // 只在有用户信息时才更新开关可用性，否则等fetchUserProfile完成后由观察者更新
        if (viewModel.userProfile.value != null) {
            val isVerified = viewModel.userProfile.value?.verified ?: false
            updateFallbackSwitchAvailability(isVerified)
        }
        
        // 最后再重新获取用户信息，确保显示最新状态
        viewModel.fetchUserProfile()
    }
}
