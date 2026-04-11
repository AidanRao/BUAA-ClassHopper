package com.example.hello.data.api

import com.example.hello.data.model.ApiResponse
import com.example.hello.data.model.dto.RateMonitorRuleDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface RateMonitorApi {

    @GET("message/feature/rate-monitor/config/currency")
    suspend fun getAvailableCurrencies(): ApiResponse<Map<String, String>>

    @GET("message/feature/rate-monitor/rules")
    suspend fun getRules(): ApiResponse<List<RateMonitorRuleDto>>

    @POST("message/feature/rate-monitor/rules")
    suspend fun addRule(@Body request: RateMonitorRuleRequest): ApiResponse<RateMonitorRuleDto>

    @PUT("message/feature/rate-monitor/rules/{ruleId}")
    suspend fun updateRule(
        @Path("ruleId") ruleId: String,
        @Body request: RateMonitorRuleRequest
    ): ApiResponse<RateMonitorRuleDto>
}

data class RateMonitorRuleRequest(
    val email: String,
    val baseCurrency: String,
    val quoteCurrency: String,
    val absoluteLower: String? = null,
    val absoluteUpper: String? = null,
    val active: Boolean? = null
)
