package io.engenious.sift

class OrchestratorDevClient(token: String, allowInsecureTls: Boolean) : OrchestratorClient(token, allowInsecureTls) {
    override val baseUrl = "https://dev.api.orchestrator.engenious.io"
}
