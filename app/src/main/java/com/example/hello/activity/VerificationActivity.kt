package com.example.hello.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.hello.NavigationManager
import com.example.hello.R
import com.example.hello.viewmodel.VerificationViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VerificationActivity : AppCompatActivity() {
    private lateinit var backButton: ImageView
    private lateinit var studentIdEdit: EditText
    private lateinit var sendCodeButton: Button
    private lateinit var verifyCodeEdit: EditText
    private lateinit var verifyButton: Button
    private var countDownTimer: CountDownTimer? = null

    private val viewModel: VerificationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        initViews()
        initListeners()
        initObservers()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    private fun initViews() {
        backButton = findViewById(R.id.back_button)
        studentIdEdit = findViewById(R.id.student_id_edit)
        sendCodeButton = findViewById(R.id.send_code_button)
        verifyCodeEdit = findViewById(R.id.verify_code_edit)
        verifyButton = findViewById(R.id.verify_button)
    }

    private fun initListeners() {
        backButton.setOnClickListener {
            handleBackNavigation()
            finish()
        }

        sendCodeButton.setOnClickListener {
            val studentId = studentIdEdit.text.toString().trim()
            if (studentId.isEmpty()) {
                Toast.makeText(this, "请输入学号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!studentId.matches(Regex("[a-zA-Z0-9]+"))) {
                Toast.makeText(this, "学号格式不正确", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.sendVerifyCode(studentId)
        }

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

            viewModel.verifyUser(studentId, verifyCode)
        }
    }

    private fun initObservers() {
        viewModel.message.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            if (message == "验证码已发送") {
                startCountDown()
            }
        }

        viewModel.error.observe(this) { error ->
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            sendCodeButton.isEnabled = true
            verifyButton.isEnabled = true
        }

        viewModel.verificationSuccess.observe(this) { success ->
            if (success) {
                finish()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            sendCodeButton.isEnabled = !isLoading
            verifyButton.isEnabled = !isLoading
        }
    }

    private fun handleBackNavigation() {
        if (isTaskRoot) {
            NavigationManager.navigate(this, "/main")
            return
        }
        finish()
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
        countDownTimer?.cancel()
    }
}
