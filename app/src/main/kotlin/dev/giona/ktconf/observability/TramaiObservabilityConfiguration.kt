package dev.giona.ktconf.observability

import dev.tramai.core.model.ModelRegistry
import dev.tramai.core.model.TramaiTool
import dev.tramai.core.observation.OperationObserver
import dev.tramai.core.provider.ModelProvider
import dev.tramai.observability.OpenTelemetryOperationObserver
import dev.tramai.observability.OpenTelemetryWorkflowObserver
import dev.tramai.orchestration.WorkflowObserver
import dev.tramai.security.audit.AuditStore
import dev.tramai.sovereign.SovereignProfileConfiguration
import dev.tramai.sovereign.SovereignTramai
import dev.tramai.spring.sovereign.SovereignTramaiAutoConfiguration.SovereignTramaiInfrastructure
import dev.tramai.spring.sovereign.SovereignTramaiProperties
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.BatchSpanProcessor
import java.time.Duration
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Adds TramAI's OpenTelemetry observer to the governed runtime.
 *
 * The application deliberately uses the deployment-provided global OpenTelemetry
 * instance: an OpenTelemetry Java agent or the host application's SDK owns the
 * exporter and OTLP configuration; this demo owns only TramAI instrumentation.
 */
@Configuration(proxyBeanMethods = false)
class TramaiObservabilityConfiguration {

    /**
     * Opt-in local/host OTLP export. The endpoint is a collector base URL;
     * this configuration adds the OTLP HTTP trace path itself.
     */
    @Bean
    @ConditionalOnProperty(prefix = "ktconf.observability", name = ["otlp-endpoint"])
    fun localOtlpOpenTelemetry(
        @Value("\${ktconf.observability.otlp-endpoint}") collectorEndpoint: String,
    ): OpenTelemetrySdk {
        val tracesEndpoint = collectorEndpoint.trimEnd('/') + "/v1/traces"
        val exporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(tracesEndpoint)
            .build()
        val tracerProvider = SdkTracerProvider.builder()
            .setResource(
                Resource.getDefault().merge(
                    Resource.create(
                        Attributes.of(AttributeKey.stringKey("service.name"), "ktconf-demo"),
                    ),
                ),
            )
            .addSpanProcessor(
                BatchSpanProcessor.builder(exporter)
                    .setScheduleDelay(Duration.ofMillis(200))
                    .build(),
            )
            .build()
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build()
    }

    @Bean
    @ConditionalOnMissingBean(OpenTelemetry::class)
    fun openTelemetry(): OpenTelemetry = GlobalOpenTelemetry.get()

    @Bean
    fun tramaiOperationObserver(openTelemetry: OpenTelemetry): OperationObserver =
        OpenTelemetryOperationObserver(openTelemetry, "dev.giona.ktconf.tramai")

    @Bean
    fun tramaiWorkflowObserver(openTelemetry: OpenTelemetry): WorkflowObserver =
        OpenTelemetryWorkflowObserver(openTelemetry, "dev.giona.ktconf.workflow")

    /**
     * The sovereign starter currently does not accept an OperationObserver
     * contribution, so this application-owned composition mirrors its normal
     * provider/tool/model registration and adds the observer at the seam.
     */
    @Bean(name = ["sovereignTramai"])
    @ConditionalOnMissingBean(SovereignTramai::class)
    fun sovereignTramai(
        profile: SovereignProfileConfiguration,
        modelRegistry: ModelRegistry,
        auditStore: AuditStore,
        modelProviders: ObjectProvider<ModelProvider>,
        toolProviders: ObjectProvider<TramaiTool<*, *>>,
        properties: SovereignTramaiProperties,
        infrastructure: SovereignTramaiInfrastructure,
        operationObserver: OperationObserver,
    ): SovereignTramai {
        val builder = SovereignTramai.builder()
            .profile(profile)
            .modelRegistry(modelRegistry)
            .auditStore(auditStore)
            .approvalGateCoordinator(infrastructure.approvalGateCoordinator)
            .approvalContinuationStore(infrastructure.approvalContinuationStore)
            .clock(infrastructure.clock)
            .observer(operationObserver)

        infrastructure.suspendedInvocationStore?.let { builder.suspendedInvocationStore(it) }
        infrastructure.toolArgumentsDigester?.let { builder.toolArgumentsDigester(it) }
        modelProviders.orderedStream().forEach { builder.provider(it, name = it.providerId()) }
        builder.tools(toolProviders.orderedStream().toList())
        properties.models.forEach { (modelName, providerName) -> builder.model(modelName, providerName) }
        return builder.build()
    }
}
