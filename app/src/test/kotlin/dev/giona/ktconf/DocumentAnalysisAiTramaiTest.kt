package dev.giona.ktconf

import dev.giona.ktconf.ai.DocumentAnalysisAi
import dev.giona.ktconf.domain.ConfidentialDocument
import dev.giona.ktconf.domain.toClassifiedDocument
import dev.tramai.core.model.RegisteredModel
import dev.tramai.core.security.DlpContentType
import dev.tramai.security.DlpRule
import dev.tramai.security.ProviderTrustZone
import dev.tramai.security.RuleBasedDlpConfiguration
import dev.tramai.security.RuleBasedDlpInterceptor
import dev.tramai.security.audit.InMemoryAuditStore
import dev.tramai.security.model.InMemoryModelRegistry
import dev.tramai.sovereign.SovereignProfileConfiguration
import dev.tramai.sovereign.SovereignTramai
import dev.tramai.testing.MockAiProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentAnalysisAiTramaiTest {

    @Test
    fun `typed document analysis receives redacted values from TramAI DLP`() {
        val provider = MockAiProvider {
            onMethod("analyzeDocument") respondWith
                """
                {
                  "documentId": "KTCONF-DLP-001",
                  "company": "Example Conference Services BV",
                  "amount": "EUR 4,280.00",
                  "summary": "Payment for conference venue services.",
                  "contactEmail": "finance@example-confidential.eu",
                  "paymentIban": "NL91ABNA0417164300"
                }
                """.trimIndent()
        }

        val service = SovereignTramai.builder()
            .profile(
                SovereignProfileConfiguration(
                    allowedModels = setOf("eu-scaleway-invoice-model"),
                    allowedProviders = setOf("mock"),
                    providerZones = mapOf("mock" to ProviderTrustZone.EU_CLOUD),
                ),
            )
            .modelRegistry(
                InMemoryModelRegistry.builder()
                    .register(
                        RegisteredModel(
                            registryEntryId = "eu-scaleway-invoice-model",
                            providerId = "mock",
                            modelName = "eu-scaleway-invoice-model",
                            revision = "test",
                        ),
                    )
                    .build(),
            )
            .auditStore(InMemoryAuditStore())
            .provider(provider, name = "mock", default = true)
            .model("eu-scaleway-invoice-model", "mock")
            .dlp(
                RuleBasedDlpInterceptor(
                    RuleBasedDlpConfiguration(
                        rules = listOf(
                            DlpRule(
                                id = "email",
                                pattern = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}",
                                replacement = "[EMAIL_REDACTED]",
                                enabledFor = setOf(DlpContentType.MODEL_OUTPUT),
                            ),
                            DlpRule(
                                id = "iban",
                                pattern = "\\b[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{10}\\b",
                                replacement = "[IBAN_REDACTED]",
                                enabledFor = setOf(DlpContentType.MODEL_OUTPUT),
                            ),
                        ),
                    ),
                ),
            )
            .build()
            .create(DocumentAnalysisAi::class)

        val result = runBlocking {
            service.analyzeDocument(
                ConfidentialDocument(
                    documentId = "KTCONF-DLP-001",
                    company = "Example Conference Services BV",
                    amount = "EUR 4,280.00",
                    purpose = "Conference venue services",
                    paymentIban = "NL91ABNA0417164300",
                    contactEmail = "finance@example-confidential.eu",
                    notes = "Please summarize the payment instructions and contact information.",
                ).toClassifiedDocument(dev.tramai.core.policy.ClassificationSource.RULE_BASED),
            )
        }

        assertEquals("KTCONF-DLP-001", result.documentId)
        assertEquals("Payment for conference venue services.", result.summary)
        assertEquals("[EMAIL_REDACTED]", result.contactEmail)
        assertEquals("[IBAN_REDACTED]", result.paymentIban)
    }
}
