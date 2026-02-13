package com.example.hello.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.hello.R
import com.example.hello.service.ApiService
import com.example.hello.viewmodel.MainViewModel

import android.content.Intent

class VerificationActivity : AppCompatActivity() {
    private lateinit var backButton: ImageView
    private lateinit var studentIdEdit: EditText
    private lateinit var sendCodeButton: Button
    private lateinit var verifyCodeEdit: EditText
    private lateinit var verifyButton: Button
    private lateinit var viewModel: MainViewModel
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        // 初始化ViewModel
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        initViews()
        initListeners()
        initObservers()
    }

    private fun initViews() {
        backButton = findViewById(R.id.back_button)
        studentIdEdit = findViewById(R.id.student_id_edit)
        sendCodeButton = findViewById(R.id.send_code_button)
        verifyCodeEdit = findViewById(R.id.verify_code_edit)
        verifyButton = findViewById(R.id.verify_button)
    }

    private fun initListeners() {
        // 返回按钮点击事件
        backButton.setOnClickListener {
            handleBackNavigation()
        }

        // 发送验证码按钮点击事件
        sendCodeButton.setOnClickListener {
            val studentId = studentIdEdit.text.toString().trim()
            if (studentId.isEmpty()) {
                Toast.makeText(this, "请输入学号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 检查学号格式是否正确（支持字母和数字）
            if (!studentId.matches(Regex("[a-zA-Z0-9]+"))) {
                Toast.makeText(this, "学号格式不正确", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 发送验证码
            sendVerifyCode(studentId)
        }

        // 验证按钮点击事件
        verifyButton.setOnClickListener {
            val studentId = studentIdEdit.text.toString().trim()
            val verifyCode = verifyCodeEdit.text.toString().trim()

            if (studentId.isEmpty()) {
                Toast.makeText(this, "请输入学号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (verifyCode.isEmpty()) {
                Toast.makeText(this, "请输入验证码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 验证身份
            verifyUser(studentId, verifyCode)
        }
    }

    private fun initObservers() {
        // 观察用户信息变化
        viewModel.userProfile.observe(this) {
            // 自动填充学号
            studentIdEdit.setText(it.studentId)
            studentIdEdit.isEnabled = false
        }
    }

    private fun handleBackNavigation() {
        if (isTaskRoot) {
            // 如果是根任务（没有上一级页面），则跳转到主页
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        finish()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackNavigation()
    }

    private fun sendVerifyCode(studentId: String) {
        viewModel.apiService.getValidToken(object : ApiService.OnAuthListener {
            override fun onSuccess(token: String, expireAt: Long) {
                runOnUiThread {
                    sendVerifyCodeWithToken(token, studentId)
                }
            }

            override fun onFailure(error: String) {
                runOnUiThread {
                    Toast.makeText(this@VerificationActivity, error, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun sendVerifyCodeWithToken(token: String, studentId: String) {
        // 禁用发送按钮，防止重复点击
        sendCodeButton.isEnabled = false

        // 发送验证码
        viewModel.apiService.sendVerifyCode(token, studentId, object : ApiService.OnVerifyCodeListener {
            override fun onSuccess(message: String) {
                runOnUiThread {
                    Toast.makeText(this@VerificationActivity, message, Toast.LENGTH_SHORT).show()
                    // 开始倒计时
                    startCountDown()
                }
            }

            override fun onFailure(error: String) {
                runOnUiThread {
                    Toast.makeText(this@VerificationActivity, error, Toast.LENGTH_SHORT).show()
                    // 启用发送按钮
                    sendCodeButton.isEnabled = true
                }
            }
        })
    }

    private fun verifyUser(studentId: String, verifyCode: String) {
        viewModel.apiService.getValidToken(object : ApiService.OnAuthListener {
            override fun onSuccess(token: String, expireAt: Long) {
                runOnUiThread {
                    verifyUserWithToken(token, studentId, verifyCode)
                }
            }

            override fun onFailure(error: String) {
                runOnUiThread {
                    Toast.makeText(this@VerificationActivity, error, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun verifyUserWithToken(token: String, studentId: String, verifyCode: String) {
        // 禁用验证按钮，防止重复点击
        verifyButton.isEnabled = false

        // 验证身份
        viewModel.apiService.verifyUser(token, studentId, verifyCode, object : ApiService.OnUserVerifyListener {
            override fun onSuccess(message: String) {
                runOnUiThread {
                    Toast.makeText(this@VerificationActivity, message, Toast.LENGTH_SHORT).show()
                    // 验证成功后关闭当前界面
                    finish()
                }
            }

            override fun onFailure(error: String) {
                runOnUiThread {
                    Toast.makeText(this@VerificationActivity, error, Toast.LENGTH_SHORT).show()
                    // 启用验证按钮
                    verifyButton.isEnabled = true
                }
            }
        })
    }

    private fun startCountDown() {
        // 取消之前的倒计时
        countDownTimer?.cancel()

        // 创建新的倒计时
        countDownTimer = object : CountDownTimer(60000, 1000) {
            @SuppressLint("SetTextI18n")
            override fun onTick(millisUntilFinished: Long) {
                sendCodeButton.text = "重新发送(${millisUntilFinished / 1000}秒)"
            }

            override fun onFinish() {
                sendCodeButton.text = "重新发送验证码"
                sendCodeButton.isEnabled = true
            }
        }

        // 启动倒计时
        countDownTimer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消倒计时
        countDownTimer?.cancel()
    }
}
