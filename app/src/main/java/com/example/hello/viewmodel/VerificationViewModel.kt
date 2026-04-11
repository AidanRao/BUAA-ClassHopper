package com.example.hello.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hello.data.model.Result
import com.example.hello.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _verificationSuccess = MutableLiveData<Boolean>()
    val verificationSuccess: LiveData<Boolean> = _verificationSuccess

    fun sendVerifyCode(studentId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = userRepository.sendVerifyCode(studentId)) {
                is Result.Success -> {
                    _message.value = "验证码已发送"
                    _isLoading.value = false
                }
                is Result.Error -> {
                    _error.value = result.getErrorMessage() ?: "发送验证码失败"
                    _isLoading.value = false
                }
                Result.Loading -> {}
            }
        }
    }

    fun verifyUser(studentId: String, verifyCode: String) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = userRepository.verifyUser(studentId, verifyCode)) {
                is Result.Success -> {
                    _message.value = "验证成功"
                    _verificationSuccess.value = true
                    _isLoading.value = false
                }
                is Result.Error -> {
                    _error.value = result.getErrorMessage() ?: "验证失败"
                    _isLoading.value = false
                }
                Result.Loading -> {}
            }
        }
    }
}
