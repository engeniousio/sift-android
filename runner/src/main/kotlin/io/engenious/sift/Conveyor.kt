package io.engenious.sift

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.Tongs
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class Conveyor private constructor() {
    companion object {
        val conveyor = Conveyor()
        val logger: Logger = LoggerFactory.getLogger(Conveyor::class.java)
    }

    private val exceptionHolder = AtomicReference<Exception>()

    var storage: Any = Unit
        private set

    private lateinit var queue: ArrayDeque<Worker<Any, Any>>

    fun <S12 : Any, S23 : Any, S34 : Any> prepare(
        initialConfiguration: Configuration.Builder.() -> Unit,
        w1: Worker<Unit, S12>,
        w2: Worker<S12, S23>,
        w3: Worker<S23, S34>,
        w4: Worker<S34, Unit>
    ): ConveyorRunnable {
        @Suppress("UNCHECKED_CAST")
        return internalPrepare(
            initialConfiguration,
            listOf(w1, w2, w3, w4) as List<Worker<Any, Any>>
        )
    }

    fun <S12 : Any, S23 : Any, S34 : Any, S45 : Any> prepare(
        initialConfiguration: Configuration.Builder.() -> Unit,
        w1: Worker<Unit, S12>,
        w2: Worker<S12, S23>,
        w3: Worker<S23, S34>,
        w4: Worker<S34, S45>,
        w5: Worker<S45, Unit>
    ): ConveyorRunnable {
        @Suppress("UNCHECKED_CAST")
        return internalPrepare(
            initialConfiguration,
            listOf(w1, w2, w3, w4, w5) as List<Worker<Any, Any>>
        )
    }

    private fun internalPrepare(
        initialConfiguration: Configuration.Builder.() -> Unit,
        workerList: List<Worker<Any, Any>>
    ): ConveyorRunnable {
        queue = ArrayDeque(workerList)

        val plugins = workerList.filterIsInstance<Plugin<Any, Any>>()
        return ConveyorRunnable(initialConfiguration, plugins)
    }

    class ConveyorRunnable(
        private val initialConfiguration: Configuration.Builder.() -> Unit,
        plugins: List<Any>
    ) {
        private val rules = ArrayList(plugins)

        fun addRule(rule: Any): ConveyorRunnable {
            rules.add(rule)
            return this
        }

        fun run(withWarnings: Boolean): Boolean {
            val configuration = Configuration.Builder()
                .apply(initialConfiguration)
                .build(withWarnings)
                .apply {
                    pluginsInstances.apply {
                        clear()
                        addAll(rules)
                    }
                }

            conveyor.finalizeAndAdvanceConveyor<Unit>()
            val result = try {
                logger.info("RUN Tongs run configuration $configuration")
                Tongs(configuration).run(allowThrows = false)
//                Tongs(configuration).run(allowThrows = true)
//            } catch (e: Exception) {
//                logger.info("RUN Tongs run Exception $e")
//                false
            } finally {
                if (conveyor.queue.isNotEmpty()) {
                    conveyor.finalizeAndAdvanceConveyor<Any>()
                }
                if (conveyor.queue.isNotEmpty()) {
                    System.err.println("Something went wrong")
                }
            }

            conveyor.exceptionHolder.get()
                ?.let {
                    logger.error("RUN conveyor.exceptionHolder $it")
                    throw it
                }

            logger.info("RUN conveyor result $result")
            return result
        }
    }

    abstract class Plugin<IN : Any, OUT : Any> : Worker<IN, OUT> {
        lateinit var previousStorage: IN

        val storage: OUT
            @Suppress("UNCHECKED_CAST")
            get() = conveyor.storage as OUT

        private val canAdvance = AtomicBoolean(true)

        abstract fun initStorage(): OUT

        final override fun call(storage: IN, ctx: Conveyor): OUT {
            @Suppress("UNCHECKED_CAST")
            previousStorage = conveyor.storage as IN

            return initStorage()
        }

        inline fun <reified T : OUT> finalizeAndAdvanceConveyor() = finalizeAndAdvanceConveyor(T::class.java)
        fun finalizeAndAdvanceConveyor(expectedStorage: Class<*>) {
            if (canAdvance.getAndSet(false)) {
                conveyor.finalizeAndAdvanceConveyor(expectedStorage)
            } else {
                throw ConveyorError("A plugin tried to advance the conveyor twice")
            }
        }
    }

    inline fun <reified OUT> requireConveyorStorage(): OUT = requireConveyorStorage(OUT::class.java)

    fun <OUT> requireConveyorStorage(expectedStorage: Class<OUT>): OUT {
        storage.let { storage ->
            if (!expectedStorage.isInstance(storage)) {
                throw ConveyorError(
                    "Conveyor is in wrong state. Expected ${expectedStorage.simpleName}, got ${storage.javaClass.simpleName}"
                )
            }
            @Suppress("UNCHECKED_CAST") //  checked above
            return (storage as OUT)
        }
    }

    private inline fun <reified T : Any> finalizeAndAdvanceConveyor() {
        finalizeAndAdvanceConveyor(T::class.java)
    }
    private fun finalizeAndAdvanceConveyor(expectedStorage: Class<*>) {
        requireConveyorStorage(expectedStorage)

        val worker = queue.removeFirst()
        storage = worker.call(storage, this)
        if (worker !is Plugin && queue.isNotEmpty()) {
            finalizeAndAdvanceConveyor<Any>()
        }
    }

    fun throwDeferred(exception: Exception) {
        exceptionHolder.compareAndSet(null, exception)
    }

    private class ConveyorError(message: String) : Error("Internal Sift error - $message")
}

fun interface Worker<IN, OUT> {
    fun call(storage: IN, ctx: Conveyor): OUT
}
