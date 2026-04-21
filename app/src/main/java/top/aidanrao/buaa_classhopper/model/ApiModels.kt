package top.aidanrao.buaa_classhopper.model

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

// Fallback API响应结构
data class FallbackScheduleResponse(
    val code: Int,
    val msg: String,
    val data: FallbackScheduleData
)

data class FallbackScheduleData(
    val status: String,
    val total: Int,
    val result: List<FallbackCourse>
)

data class FallbackCourse(
    val id: Int,
    val courseId: Int,
    val courseName: String,
    val courseType: String,
    val weekDay: String,
    val courseNum: String,
    val teacherName: String,
    val classroomName: String,
    val signStatus: String, // "未签到" 或 "已签到"
    val classBeginTime: String, // 字符串格式的时间
    val classEndTime: String // 字符串格式的时间
)