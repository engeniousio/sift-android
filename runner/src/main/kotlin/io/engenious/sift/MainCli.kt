package io.engenious.sift

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val parser = ArgParser("sift")
    val command: Command by parser.argument(
        EnumArgChoice.enumArgChoice(),
        description = "Command to execute"
    )
    val configPath by parser.option(
        ArgType.String,
        "config",
        "c",
        "Path to the configuration file"
    ).required()

    parser.parse(args)

    val config = File(configPath)
    val sift = Sift(config)

    val exitCode = when (command) {
        Command.INIT -> sift.initOrchestrator()
        Command.LIST -> sift.list()
        Command.RUN -> sift.run()
    }
    exitProcess(exitCode)
}

enum class Command {
    INIT,
    LIST,
    RUN
}
