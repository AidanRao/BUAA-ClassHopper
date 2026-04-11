package com.example.hello.data.model.dto

data class UserInfoDto(
    val id: String,
    val username: String,
    val avatar: String?,
    val createTime: String,
    val gender: String,
    val studentId: String,
    val verified: Boolean,
    val joinedTeamCount: Int,
    val createdTeamCount: Int,
    val role: String,
    val appKey: String?
)
