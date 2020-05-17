package io.engenious.sift

import io.engenious.sift.Sift
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("sift")
    val command by parser.argument(
            ArgType.Choice(listOf("run")),
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
    Sift(config).run()
}
