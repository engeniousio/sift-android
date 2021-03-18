package io.engenious.sift

class SiftDevClient(private val token: String, allowInsecureTls: Boolean) : SiftClient(token, allowInsecureTls) {
    override val baseUrl = "https://dev.api.orchestrator.engenious.io"
}
