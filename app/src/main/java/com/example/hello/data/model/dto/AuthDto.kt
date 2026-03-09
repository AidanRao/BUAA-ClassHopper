package com.example.hello.data.model.dto

data class AuthRequest(
    val appKey: String,
    val timestamp: Long,
    val signature: String,
    val appUUID: String
)

data class AuthResponse(
    val token: String,
    val expireAt: String
)
