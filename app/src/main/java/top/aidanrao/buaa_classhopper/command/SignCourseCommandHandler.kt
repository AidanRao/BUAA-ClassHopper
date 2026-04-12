package top.aidanrao.buaa_classhopper.command

import android.content.Context
import top.aidanrao.buaa_classhopper.AppApplication
import top.aidanrao.buaa_classhopper.data.model.Result
import top.aidanrao.buaa_classhopper.command.model.CommandDTO
import top.aidanrao.buaa_classhopper.command.model.CommandExecutionResult
import top.aidanrao.buaa_classhopper.data.repository.CourseRepository
import top.aidanrao.buaa_classhopper.di.RepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking

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

        return runBlocking {
            try {
                val courseRepository = getCourseRepository()

                when (val result = courseRepository.signClass(studentId, courseId)) {
                    is Result.Success -> {
                        CommandExecutionResult(
                            commandId = command.commandId,
                            success = true,
                            message = "Sign course successful"
                        )
                    }
                    is Result.Error -> {
                        CommandExecutionResult(
                            commandId = command.commandId,
                            success = false,
                            message = result.getErrorMessage() ?: "Sign course failed"
                        )
                    }
                    Result.Loading -> {
                        CommandExecutionResult(
                            commandId = command.commandId,
                            success = false,
                            message = "Sign course loading"
                        )
                    }
                }
            } catch (e: Exception) {
                CommandExecutionResult(
                    commandId = command.commandId,
                    success = false,
                    message = "Sign course failed: ${e.message}"
                )
            }
        }
    }

    private fun getCourseRepository(): CourseRepository {
        val appContext = AppApplication.instance.applicationContext
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            appContext,
            RepositoryEntryPoint::class.java
        )
        return hiltEntryPoint.courseRepository()
    }
}
