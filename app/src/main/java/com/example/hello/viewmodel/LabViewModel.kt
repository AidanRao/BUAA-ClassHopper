package com.example.hello.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hello.data.api.RateMonitorRuleRequest
import com.example.hello.data.model.Result
import com.example.hello.data.model.dto.RateMonitorRuleDto
import com.example.hello.data.repository.LabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LabViewModel @Inject constructor(
    private val labRepository: LabRepository
) : ViewModel() {

    private val _features = MutableLiveData<List<String>>()
    val features: LiveData<List<String>> = _features

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun loadFeatures() {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = labRepository.getAvailableFeatures()) {
                is Result.Success -> {
                    _features.value = result.data
                    _isLoading.value = false
                }
                is Result.Error -> {
                    _error.value = result.getErrorMessage() ?: "加载功能列表失败"
                    _isLoading.value = false
                }
                Result.Loading -> {}
            }
        }
    }
}

@HiltViewModel
class RateMonitorViewModel @Inject constructor(
    private val labRepository: LabRepository
) : ViewModel() {

    private val _currencies = MutableLiveData<Map<String, String>>()
    val currencies: LiveData<Map<String, String>> = _currencies

    private val _rules = MutableLiveData<List<RateMonitorRuleDto>>()
    val rules: LiveData<List<RateMonitorRuleDto>> = _rules

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _operationSuccess = MutableLiveData<Boolean>()
    val operationSuccess: LiveData<Boolean> = _operationSuccess

    fun loadCurrencies() {
        viewModelScope.launch {
            when (val result = labRepository.getAvailableCurrencies()) {
                is Result.Success -> {
                    _currencies.value = result.data
                }
                is Result.Error -> {
                    _error.value = result.getErrorMessage() ?: "加载货币列表失败"
                }
                Result.Loading -> {}
            }
        }
    }

    fun loadRules() {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = labRepository.getRateMonitorRules()) {
                is Result.Success -> {
                    _rules.value = result.data
                    _isLoading.value = false
                }
                is Result.Error -> {
                    _error.value = result.getErrorMessage() ?: "加载监控规则失败"
                    _isLoading.value = false
                }
                Result.Loading -> {}
            }
        }
    }

    fun addRule(request: RateMonitorRuleRequest) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = labRepository.addRateMonitorRule(request)) {
                is Result.Success -> {
                    _operationSuccess.value = true
                    loadRules()
                }
                is Result.Error -> {
                    _error.value = result.getErrorMessage() ?: "添加规则失败"
                    _isLoading.value = false
                }
                Result.Loading -> {}
            }
        }
    }

    fun updateRule(ruleId: String, request: RateMonitorRuleRequest) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = labRepository.updateRateMonitorRule(ruleId, request)) {
                is Result.Success -> {
                    _operationSuccess.value = true
                    loadRules()
                }
                is Result.Error -> {
                    _error.value = result.getErrorMessage() ?: "更新规则失败"
                    _isLoading.value = false
                }
                Result.Loading -> {}
            }
        }
    }

    fun toggleRuleStatus(rule: RateMonitorRuleDto) {
        _isLoading.value = true
        viewModelScope.launch {
            val request = RateMonitorRuleRequest(
                email = rule.email,
                baseCurrency = rule.baseCurrency,
                quoteCurrency = rule.quoteCurrency,
                absoluteLower = rule.thresholds.absoluteLower.toString(),
                absoluteUpper = rule.thresholds.absoluteUpper.toString(),
                active = !rule.active
            )
            when (val result = labRepository.updateRateMonitorRule(rule.id, request)) {
                is Result.Success -> {
                    _operationSuccess.value = true
                    loadRules()
                }
                is Result.Error -> {
                    _error.value = result.getErrorMessage() ?: "切换状态失败"
                    _isLoading.value = false
                }
                Result.Loading -> {}
            }
        }
    }
}
