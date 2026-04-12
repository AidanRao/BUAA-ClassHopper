package top.aidanrao.buaa_classhopper.data.repository

import top.aidanrao.buaa_classhopper.data.api.CommonRequest
import top.aidanrao.buaa_classhopper.data.api.LabApi
import top.aidanrao.buaa_classhopper.data.api.RateMonitorApi
import top.aidanrao.buaa_classhopper.data.api.RateMonitorRuleRequest
import top.aidanrao.buaa_classhopper.data.model.Result
import top.aidanrao.buaa_classhopper.data.model.dto.RateMonitorRuleDto
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
