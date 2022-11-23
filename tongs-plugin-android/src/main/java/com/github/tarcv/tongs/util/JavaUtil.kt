package com.github.tarcv.tongs.util

fun guessPackage(className: String): String {
    return className.split(".")
            .takeWhile { it.firstOrNull()?.isLowerCase() == true }
            .joinToString(".")
}