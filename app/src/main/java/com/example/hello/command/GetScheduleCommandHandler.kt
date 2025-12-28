package com.example.hello.command

import android.content.Context
import com.example.hello.command.model.CommandDTO
import com.example.hello.command.model.CommandExecutionResult
import com.example.hello.model.Course
import com.example.hello.model.ScheduleVO
import com.example.hello.service.IclassApiService

class GetScheduleCommandHandler(private val context: Context) : CommandHandler {
    override fun getCommandType(): String = "getSchedule"

    override fun execute(command: CommandDTO): CommandExecutionResult {
        // 验证参数
        val studentId = command.params?.get("studentId") as? String
        val date = command.params?.get("date") as? String
        
        if (studentId.isNullOrEmpty() || date.isNullOrEmpty()) {
            return CommandExecutionResult(
                commandId = command.commandId,
                success = false,
                message = "Missing required parameters: studentId or date"
            )
        }

        // 由于ApiService的方法是异步的，而CommandHandler的execute方法是同步的
        // 我们需要使用一个同步的方式来等待结果
        val lock = Object()
        var result: CommandExecutionResult? = null

        // 首先登录获取userId和sessionId
        val iclassApiService = IclassApiService(context)
        iclassApiService.login(studentId, object : IclassApiService.OnLoginListener {
            override fun onSuccess(userId: String, sessionId: String, realName: String, academyName: String) {
                val dateStr = date.replace("-", "")
                // 获取课程表
                iclassApiService.getCourseSchedule(userId, sessionId, dateStr, object : IclassApiService.OnCourseScheduleListener {
                    override fun onSuccess(courses: List<Course>) {
                        synchronized(lock) {
                            // 创建ScheduleVO对象
                            val scheduleVO = ScheduleVO(
                                status = "success",
                                total = courses.size,
                                result = courses
                            )
                            
                            result = CommandExecutionResult(
                                commandId = command.commandId,
                                success = true,
                                message = "Get schedule successful",
                                data = scheduleVO
                            )
                            lock.notify()
                        }
                    }

                    override fun onEmpty() {
                        synchronized(lock) {
                            // 创建空的ScheduleVO对象
                            val scheduleVO = ScheduleVO(
                                status = "success",
                                total = 0,
                                result = emptyList()
                            )
                            
                            result = CommandExecutionResult(
                                commandId = command.commandId,
                                success = true,
                                message = "No courses found",
                                data = scheduleVO
                            )
                            lock.notify()
                        }
                    }

                    override fun onFailure(error: String) {
                        synchronized(lock) {
                            result = CommandExecutionResult(
                                commandId = command.commandId,
                                success = false,
                                message = "Get schedule failed: $error"
                            )
                            lock.notify()
                        }
                    }
                })
            }

            override fun onFailure(error: String) {
                synchronized(lock) {
                    result = CommandExecutionResult(
                        commandId = command.commandId,
                        success = false,
                        message = "Login failed: $error"
                    )
                    lock.notify()
                }
            }
        })

        // 等待结果
        synchronized(lock) {
            if (result == null) {
                try {
                    lock.wait(10000) // 10秒超时
                } catch (e: InterruptedException) {
                    return CommandExecutionResult(
                        commandId = command.commandId,
                        success = false,
                        message = "Get schedule interrupted"
                    )
                }
            }
        }

        return result ?: CommandExecutionResult(
            commandId = command.commandId,
            success = false,
            message = "Get schedule timed out"
        )
    }
}