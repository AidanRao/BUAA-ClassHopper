package com.example.hello.data.repository

import com.example.hello.data.api.CommonRequest
import com.example.hello.data.api.LabApi
import com.example.hello.data.api.RateMonitorApi
import com.example.hello.data.api.RateMonitorRuleRequest
import com.example.hello.data.model.Result
import com.example.hello.data.model.dto.RateMonitorRuleDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LabRepository @Inject constructor(
    private val labApi: LabApi,
    private val rateMonitorApi: RateMonitorApi
) {

    suspend fun getAvailableFeatures(): Result<List<String>> {
        return CommonRequest.safeApiCallWithResponse { labApi.getAvailableFeatures() }
    }

    suspend fun getAvailableCurrencies(): Result<Map<String, String>> {
        return CommonRequest.safeApiCallWithResponse { rateMonitorApi.getAvailableCurrencies() }
    }

    suspend fun getRateMonitorRules(): Result<List<RateMonitorRuleDto>> {
        return CommonRequest.safeApiCallWithResponse { rateMonitorApi.getRules() }
    }

    suspend fun addRateMonitorRule(request: RateMonitorRuleRequest): Result<RateMonitorRuleDto> {
        return CommonRequest.safeApiCallWithResponse { rateMonitorApi.addRule(request) }
    }

    suspend fun updateRateMonitorRule(ruleId: String, request: RateMonitorRuleRequest): Result<RateMonitorRuleDto> {
        return CommonRequest.safeApiCallWithResponse { rateMonitorApi.updateRule(ruleId, request) }
    }
}
