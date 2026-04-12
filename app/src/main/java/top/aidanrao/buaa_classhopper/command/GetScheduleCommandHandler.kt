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

        return runBlocking {
            try {
                val courseRepository = getCourseRepository()
                
                val loginResult = courseRepository.login(studentId)
                if (loginResult.isError) {
                    return@runBlocking CommandExecutionResult(
                        commandId = command.commandId,
                        success = false,
                        message = "Login failed: ${loginResult.getErrorMessage()}"
                    )
                }

                val loginData =
                    loginResult.getOrThrow().result ?: return@runBlocking CommandExecutionResult(
                        commandId = command.commandId,
                        success = false,
                        message = "Login failed: User not found"
                    )
                val dateStr = date.replace("-", "")
                
                when (val scheduleResult = courseRepository.getCourseSchedule(loginData.id, loginData.sessionId, dateStr)) {
                    is Result.Success -> {
                        val courses = scheduleResult.data
                        CommandExecutionResult(
                            commandId = command.commandId,
                            success = true,
                            message = if (courses.isEmpty()) "No courses found" else "Get schedule successful",
                            data = mapOf(
                                "status" to "success",
                                "total" to courses.size,
                                "result" to courses
                            )
                        )
                    }
                    is Result.Error -> {
                        CommandExecutionResult(
                            commandId = command.commandId,
                            success = false,
                            message = "Get schedule failed: ${scheduleResult.getErrorMessage()}"
                        )
                    }
                    Result.Loading -> {
                        CommandExecutionResult(
                            commandId = command.commandId,
                            success = false,
                            message = "Get schedule loading"
                        )
                    }
                }
            } catch (e: Exception) {
                CommandExecutionResult(
                    commandId = command.commandId,
                    success = false,
                    message = "Get schedule failed: ${e.message}"
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
