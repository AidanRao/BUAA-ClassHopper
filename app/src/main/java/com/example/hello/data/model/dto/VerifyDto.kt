package com.example.hello.data.model.dto

data class QRCodeInfoDto(
    val status: String?,
    val token: String?,
    val expireAt: String?,
    val ipAddress: String?,
    val browser: String?,
    val os: String?,
    val device: String?,
    val requestTime: String?
)

data class VerifyCodeRequest(
    val email: String,
    val type: Int
)

data class UserVerifyRequest(
    val email: String,
    val verifyCode: String
)
