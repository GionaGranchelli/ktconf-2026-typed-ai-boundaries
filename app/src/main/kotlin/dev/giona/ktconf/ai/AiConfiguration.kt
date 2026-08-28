package dev.giona.ktconf.ai

import dev.tramai.sovereign.SovereignTramaiRuntime
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.slf4j.LoggerFactory

/** Creates the typed AI boundary from the auto-configured sovereign runtime. */
@Configuration
class AiConfiguration {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun invoiceAnalysisService(
        runtime: SovereignTramaiRuntime,
    ): InvoiceAnalysisService {
        log.info("Creating typed InvoiceAnalysisService proxy from SovereignTramaiRuntime")
        return runtime.create(InvoiceAnalysisService::class)
    }
}
