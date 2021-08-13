package io.engenious.sift.node.serialization

import com.github.tarcv.tongs.api.result.Table
import kotlinx.serialization.Serializable

object TableJsonSerializer : SurrogateSerializer<Table.TableJson, TableJsonSerializer.SurrogateTableJson>(
    SurrogateTableJson.serializer()
) {
    override fun toSurrogate(value: Table.TableJson) = SurrogateTableJson(
        value.headers?.toList() ?: emptyList(),
        value.rows?.toList() ?: emptyList()
    )

    override fun fromSurrogate(surrogate: SurrogateTableJson) = Table.TableJson(
        surrogate.headers,
        surrogate.rows
    )

    @Serializable
    data class SurrogateTableJson(
        val headers: List<String>,
        val rows: List<Collection<String>>
    )
}
