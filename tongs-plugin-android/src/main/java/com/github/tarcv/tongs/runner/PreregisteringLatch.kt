package com.github.tarcv.tongs.runner

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PreregisteringLatch {
    private val ownerThread = Thread.currentThread()
    private var numThreads = 0L
    private val maxThreads: Int = 20
    private val latch = CountDownLatch(maxThreads)

    /**
     * Must be called outside thread to be registered (A thread must not register itself)
     */
    fun register() {
        if (Thread.currentThread() != ownerThread) {
            throw IllegalStateException()
        }
//        println("${System.identityHashCode(latch)} Register from: ${getDebugStack()}")
        ++numThreads;
        if (numThreads > maxThreads) {
            throw IllegalStateException("Waiting for more than $maxThreads threads is not supported")
        }
    }

    fun finalizeRegistering() {
        if (Thread.currentThread() != ownerThread) {
            throw IllegalStateException()
        }
        repeat((maxThreads - numThreads).toInt()) {
            latch.countDown()
        }
        if (latch.count != numThreads) {
            throw IllegalStateException()
        }
    }

    fun countDown() {
//        println("${System.identityHashCode(latch)} Countdown to ${latch.count - 1} from: ${getDebugStack()}")
        latch.countDown()
    }

    fun await(timeout: Long, unit: TimeUnit): Boolean {
        if (Thread.currentThread() != ownerThread) {
            throw IllegalStateException()
        }
        return latch.await(timeout, unit)
    }
}

private fun getDebugStack() =
        Thread.currentThread().stackTrace
                .map { it.className + "#" + it.methodName }
                .filter { it.contains("listener", ignoreCase = true) }