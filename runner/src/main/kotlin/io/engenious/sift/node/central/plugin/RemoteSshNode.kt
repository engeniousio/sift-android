package io.engenious.sift.node.central.plugin

import io.engenious.sift.Config

class RemoteSshNode(
    config: Config.NodeConfig.WithInjectedCentralNodeVars,
    val client: RemoteNodeClient
) {
    val name: String = config.name
    val uniqueIdentifier: Any = config
}
