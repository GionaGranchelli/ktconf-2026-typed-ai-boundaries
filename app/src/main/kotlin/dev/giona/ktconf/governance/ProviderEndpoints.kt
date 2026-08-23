package dev.giona.ktconf.governance

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Deployment configuration for the two provider identities
 * (`ktconf.providers.*` in application.yml).
 *
 * This is deployment ONLY — the governance truth lives in
 * `tramai.sovereign.*`. Empty values mean "use the deterministic provider
 * for this identity"; a configured endpoint/key switches the same identity
 * to a real OpenAI-compatible endpoint without touching policy.
 */
@ConfigurationProperties(prefix = "ktconf.providers")
data class ProviderEndpoints(
    val local: Endpoint = Endpoint(),
    val cloud: Endpoint = Endpoint(),
)

data class Endpoint(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
)
