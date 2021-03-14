package io.engenious.sift

import com.github.ajalt.clikt.core.subcommands

fun main(args: Array<String>) = SiftMain
    .subcommands(Sift.List, Sift.Run)
    .main(args)
