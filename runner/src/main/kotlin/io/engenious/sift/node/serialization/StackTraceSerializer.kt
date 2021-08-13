package io.engenious.sift.node.serialization

import com.github.tarcv.tongs.api.result.StackTrace
import kotlinx.serialization.Serializable

object StackTraceSerializer : SurrogateSerializer<StackTrace, StackTraceSerializer.SurrogateStackTrace>(
    SurrogateStackTrace.serializer()
) {
    @Serializable
    data class SurrogateStackTrace(val errorMessage: String, val errorType: String, val fullTrace: String)

    override fun toSurrogate(value: StackTrace) = SurrogateStackTrace(
        value.errorMessage,
        value.errorType,
        value.fullTrace
    )

    override fun fromSurrogate(surrogate: SurrogateStackTrace) = StackTrace(
        surrogate.errorMessage,
        surrogate.errorType,
        surrogate.fullTrace
    )
}
