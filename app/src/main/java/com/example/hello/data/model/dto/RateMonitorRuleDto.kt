package com.example.hello.data.model.dto

data class RateMonitorRuleDto(
    val id: String,
    val userId: String,
    val email: String,
    val baseCurrency: String,
    val quoteCurrency: String,
    val thresholds: ThresholdsDto,
    val active: Boolean,
    val createTime: String,
    val updateTime: String
)

data class ThresholdsDto(
    val absoluteUpper: Double,
    val absoluteLower: Double,
    val hysteresisMargin: Double,
    val relativeIncreasePercentage: Double
)
