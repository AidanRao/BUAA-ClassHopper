package com.example.hello.model

import java.time.LocalDateTime

data class ApiResponse(
    val code: Int,
    val msg: String,
    val data: String
)

data class ScheduleVO(
    val status: String,
    val total: Int,
    val result: List<Course>? = null
)

data class Course(
    val id: Int,
    val courseId: Int,
    val courseName: String,
    val courseType: String,
    val weekDay: String,
    val courseNum: String,
    val teacherName: String,
    val classroomName: String,
    val signStatus: Int, // 0 表示未签到，1 表示已签到
    val classBeginTime: LocalDateTime,
    val classEndTime: LocalDateTime
)