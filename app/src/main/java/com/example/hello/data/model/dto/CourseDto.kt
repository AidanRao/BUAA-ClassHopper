package com.example.hello.data.model.dto

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime

data class CourseDto(
    val id: Int,
    val courseId: Int,
    val courseName: String,
    val courseType: String,
    val weekDay: String,
    val courseNum: String,
    val teacherName: String,
    val classroomName: String,
    val signStatus: Int,
    val classBeginTime: LocalDateTime,
    val classEndTime: LocalDateTime
)

data class IclassLoginResponse(
    val result: IclassLoginResult? = null,
    val STATUS: String? = null,
    val ERRCODE: String? = null,
    val ERRMSG: String? = null
)

data class IclassLoginResult(
    val id: String,
    val sessionId: String,
    val realName: String,
    val academyName: String
)

data class IclassScheduleResponse(
    val status: String,
    val total: Int,
    val result: List<CourseDto>?
)

data class IclassSignResponse(
    val result: String?,
    val msg: String?
)

data class FallbackCourseDto(
    val id: Int,
    val courseId: Int,
    val courseName: String,
    val courseType: String,
    val weekDay: String,
    val courseNum: String,
    val teacherName: String,
    val classroomName: String,
    val signStatus: String,
    val classBeginTime: String,
    val classEndTime: String
)

data class FallbackScheduleResponse(
    val code: Int,
    val msg: String,
    val data: FallbackScheduleData
)

data class FallbackScheduleData(
    val status: String,
    val total: Int,
    val result: List<FallbackCourseDto>
)
