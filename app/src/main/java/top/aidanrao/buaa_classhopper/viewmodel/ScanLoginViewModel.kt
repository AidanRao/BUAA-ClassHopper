package top.aidanrao.buaa_classhopper.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.aidanrao.buaa_classhopper.data.model.Result
import top.aidanrao.buaa_classhopper.data.model.dto.QRCodeInfoDto
import top.aidanrao.buaa_classhopper.data.repository.QRCodeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanLoginViewModel @Inject constructor(
    private val qrCodeRepository: QRCodeRepository
) : ViewModel() {

    private val _qrCodeInfo = MutableLiveData<QRCodeInfoDto>()
    val qrCodeInfo: LiveData<QRCodeInfoDto> = _qrCodeInfo

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _confirmSuccess = MutableLiveData<Boolean>()
    val confirmSuccess: LiveData<Boolean> = _confirmSuccess

    fun loadQRCodeInfo(scanId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = qrCodeRepository.getQRCodeInfo(scanId)) {
                is Result.Success -> {
                    _qrCodeInfo.value = result.data
                    _isLoading.value = false
                }
                is Result.Error -> {
                    _error.value = result.getErrorMessage() ?: "获取扫码信息失败"
                    _isLoading.value = false
                }
                Result.Loading -> {}
            }
        }
    }

    fun confirmLogin(scanId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = qrCodeRepository.confirmQRCodeLogin(scanId)) {
                is Result.Success -> {
                    _confirmSuccess.value = true
                    _isLoading.value = false
                }
                is Result.Error -> {
                    _error.value = result.getErrorMessage() ?: "登录确认失败"
                    _isLoading.value = false
                }
                Result.Loading -> {}
            }
        }
    }
}
