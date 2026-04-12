package top.aidanrao.buaa_classhopper.command

import top.aidanrao.buaa_classhopper.command.model.CommandDTO
import top.aidanrao.buaa_classhopper.command.model.CommandExecutionResult

interface CommandHandler {
    fun execute(command: CommandDTO): CommandExecutionResult
    fun getCommandType(): String
}
