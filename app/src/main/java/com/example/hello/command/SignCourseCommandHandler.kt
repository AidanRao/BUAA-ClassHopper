package com.example.hello.command

import android.content.Context
import com.example.hello.command.model.CommandDTO
import com.example.hello.command.model.CommandExecutionResult
import com.example.hello.service.IclassApiService

class SignCourseCommandHandler(private val context: Context) : CommandHandler {
    override fun getCommandType(): String = "signCourse"

    override fun execute(command: CommandDTO): CommandExecutionResult {
        // 验证参数
        val studentId = command.params?.get("studentId") as? String
        val courseScheduleId = command.params?.get("courseScheduleId")
        
        if (studentId.isNullOrEmpty() || courseScheduleId == null) {
            return CommandExecutionResult(
                commandId = command.commandId,
                success = false,
                message = "Missing required parameters: studentId or courseScheduleId"
            )
        }

        // 转换courseScheduleId为Int
        val courseId = when (courseScheduleId) {
            is Int -> courseScheduleId
            is Number -> courseScheduleId.toInt()
            is String -> courseScheduleId.toIntOrNull()
            else -> null
        }
        
        if (courseId == null) {
            return CommandExecutionResult(
                commandId = command.commandId,
                success = false,
                message = "Invalid courseScheduleId format"
            )
        }

        // 由于ApiService的方法是异步的，而CommandHandler的execute方法是同步的
        // 我们需要使用一个同步的方式来等待结果
        val lock = Object()
        var result: CommandExecutionResult? = null

        val iclassApiService = IclassApiService(context)
        iclassApiService.signClass(studentId, courseId, object : IclassApiService.OnSignListener {
            override fun onSuccess() {
                synchronized(lock) {
                    result = CommandExecutionResult(
                        commandId = command.commandId,
                        success = true,
                        message = "Sign course successful"
                    )
                    lock.notify()
                }
            }

            override fun onFailure(error: String) {
                synchronized(lock) {
                    result = CommandExecutionResult(
                        commandId = command.commandId,
                        success = false,
                        message = error
                    )
                    lock.notify()
                }
            }
        }, null)

        // 等待结果
        synchronized(lock) {
            if (result == null) {
                try {
                    lock.wait(10000) // 10秒超时
                } catch (e: InterruptedException) {
                    return CommandExecutionResult(
                        commandId = command.commandId,
                        success = false,
                        message = "Sign course interrupted"
                    )
                }
            }
        }

        return result ?: CommandExecutionResult(
            commandId = command.commandId,
            success = false,
            message = "Sign course timed out"
        )
    }
}