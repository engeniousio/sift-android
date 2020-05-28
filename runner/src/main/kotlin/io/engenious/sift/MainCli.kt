package io.engenious.sift

import io.engenious.sift.Sift
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import java.io.File

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

    exhaustive(when (command) {
        Command.LIST -> sift.list()
        Command.RUN -> sift.run()
    })
}

enum class Command {
    LIST,
    RUN
}

private fun exhaustive(unused: Any) {}