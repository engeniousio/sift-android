package io.engenious.sift.node.central.plugin

import io.engenious.sift.OrchestratorConfig

class RemoteSshNode(
    config: OrchestratorConfig.Node.RemoteNode,
    val client: RemoteNodeClient
) {
    val name: String = config.name
    val uniqueIdentifier: Any = config
}
