package io.squados.boot;

import io.squados.approval.InProcessApprovalStore;
import io.squados.config.SquadConfig;
import io.squados.config.SquadConfigBridge;
import io.squados.config.SquadConfigParser;
import io.squados.context.SquadContext;
import io.squados.context.TokenBudget;
import io.squados.conversation.ConversationStore;
import io.squados.conversation.InProcessConversationStore;
import io.squados.durable.DurableStore;
import io.squados.durable.InProcessDurableStore;
import io.squados.durable.RedisDurableStore;
import io.squados.event.InProcessEventBus;
import io.squados.guardrail.GuardrailEngine;
import io.squados.improve.InProcessFeedbackStore;
import io.squados.llm.LlmPort;
import io.squados.mcp.McpToolProvider;
import io.squados.memory.MemoryManager;
import io.squados.memory.retrieval.EmbeddingPort;
import io.squados.metrics.MetricsPort;
import io.squados.ratelimit.RateLimitEnforcer;
import io.squados.security.AuditLog;
import io.squados.security.JwtValidator;
import io.squados.security.SecurityGuard;
import io.squados.trace.InMemoryTraceExporter;
import io.squados.trace.LogTraceExporter;
import io.squados.trace.SquadTracer;
import io.squados.trace.TraceExporter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Spring Boot Auto-Configuration for SquadOS.
 *
 * Automatically wires all SquadOS beans when squad-spring-boot-starter is on
 * the classpath. Zero boilerplate required in the application.
 *
 * ── LLM Provider — MUST be supplied by the application ────────────────────
 *
 * squad-spring-boot-starter does NOT include any LLM model dependency.
 * Add exactly ONE model starter to your application pom.xml:
 *
 *   Ollama (local):     spring-ai-starter-model-ollama
 *   OpenAI:             spring-ai-starter-model-openai
 *   Anthropic Claude:   spring-ai-starter-model-anthropic
 *   AWS Bedrock:        spring-ai-starter-model-bedrock
 *   Azure OpenAI:       spring-ai-starter-model-azure-openai
 *
 * Then configure:
 *   squad.llm.provider=openai
 *   squad.llm.model=gpt-4o
 *
 * If no Spring AI model is on the classpath, SquadOS boots with MockLlmPort
 * (deterministic responses — safe for local dev and tests).
 * ──────────────────────────────────────────────────────────────────────────
 *
 * Beans auto-created:
 *   Always:
 *     LlmPort (SpringAiLlmAdapter if ChatClient.Builder present, else MockLlmPort)
 *     SquadContext, TraceExporter, RateLimitEnforcer
 *     RemoteSquadInjector (BeanPostProcessor for @RemoteSquad)
 *     InProcessEventBus, InProcessFeedbackStore
 *
 *   Conditional (squad.approval.enabled=true, default true):
 *     InProcessApprovalStore
 *
 *   Conditional (squad.security.enabled=true):
 *     AuditLog, SecurityGuard, JwtValidator
 *
 *   Conditional (squad.guardrails.enabled=true):
 *     GuardrailEngine
 *
 *   Conditional (squad.durable.enabled=true):
 *     DurableStore (InProcess or Redis based on squad.durable.store)
 *
 *   Conditional (squad.conversation.enabled=true):
 *     ConversationStore
 *
 *   Conditional (squad.mcp.enabled=true):
 *     McpToolProvider (HttpMcpClient)
 *
 *   Conditional (squad.agent-api.enabled=true + spring-web on classpath):
 *     AgentApiRegistrar
 *
 *   Conditional (squad.memory.enabled=true + EmbeddingModel present):
 *     SpringAiEmbeddingAdapter (EmbeddingPort)
 *
 * All beans are {@code @ConditionalOnMissingBean} — declare your own {@code @Bean}
 * to override any of them.
 */
@AutoConfiguration
@EnableConfigurationProperties(SquadProperties.class)
public class SquadAutoConfiguration {

    private final SquadProperties props;

    public SquadAutoConfiguration(SquadProperties props) {
        this.props = props;
        SquadConfigBridge.applyToSystemProperties();
    }

    // ── LLM Port ──────────────────────────────────────────────────────────
    // Moved to separate LlmPortConfig to avoid early instantiation caused by
    // squadRemoteSquadInjector BeanPostProcessor.

    // ── Squad Context ──────────────────────────────────────────────────────
    // Accepts all optional collaborators via ObjectProvider — no hard wiring required.
    // Each collaborator is injected only if its bean exists (conditional on own @Bean).

    @Bean
    @ConditionalOnMissingBean(SquadContext.class)
    public SquadContext squadContext(
            LlmPort llmPort,
            ObjectProvider<MemoryManager>     memoryManagerProvider,
            ObjectProvider<ConversationStore> conversationStoreProvider,
            ObjectProvider<RateLimitEnforcer> rateLimitEnforcerProvider,
            ObjectProvider<TokenBudget>       tokenBudgetProvider,
            ObjectProvider<McpToolProvider>   mcpToolProviderProvider,
            ObjectProvider<DurableStore>      durableStoreProvider,
            ObjectProvider<GuardrailEngine>   guardrailEngineProvider,
            ObjectProvider<MetricsPort>       metricsPortProvider,
            ObjectProvider<EmbeddingPort>     embeddingPortProvider) {

        System.out.printf("[SquadOS] Booting squad: %s%n", props.getName());
        SquadConfig config = SquadConfigParser.load();
        SquadContext ctx = new SquadContext(config, llmPort);

        // Wire optional collaborators before boot() — each no-ops if bean absent
        memoryManagerProvider    .ifAvailable(ctx::setMemoryManager);
        conversationStoreProvider.ifAvailable(ctx::setConversationStore);
        rateLimitEnforcerProvider.ifAvailable(ctx::setRateLimitEnforcer);
        tokenBudgetProvider      .ifAvailable(ctx::setTokenBudget);
        mcpToolProviderProvider  .ifAvailable(ctx::setMcpToolProvider);
        durableStoreProvider     .ifAvailable(ctx::setDurableStore);
        guardrailEngineProvider  .ifAvailable(ctx::setGuardrailEngine);
        metricsPortProvider      .ifAvailable(ctx::setMetricsPort);
        embeddingPortProvider    .ifAvailable(ctx::setEmbeddingPort);

        ctx.boot();
        return ctx;
    }

    // ── Tracing ────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(TraceExporter.class)
    @ConditionalOnProperty(prefix = "squad.tracing", name = "enabled",
                           havingValue = "true", matchIfMissing = true)
    public TraceExporter squadTraceExporter() {
        TraceExporter exporter = switch (props.getTracing().getExporter()) {
            case "memory" -> {
                System.out.println("[SquadOS] TraceExporter: in-memory");
                yield new InMemoryTraceExporter();
            }
            default -> {
                System.out.println("[SquadOS] TraceExporter: log (stdout)");
                yield new LogTraceExporter();
            }
        };
        SquadTracer.configure(exporter);
        return exporter;
    }

    // ── Approval ────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(InProcessApprovalStore.class)
    @ConditionalOnProperty(prefix = "squad.approval", name = "enabled",
                           havingValue = "true", matchIfMissing = true)
    public InProcessApprovalStore squadApprovalStore() {
        System.out.println("[SquadOS] ApprovalStore: in-process");
        return new InProcessApprovalStore();
    }

    // ── Event Bus ────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(InProcessEventBus.class)
    public InProcessEventBus squadEventBus() {
        System.out.println("[SquadOS] EventBus: in-process");
        return new InProcessEventBus();
    }

    // ── Feedback Store ───────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(InProcessFeedbackStore.class)
    public InProcessFeedbackStore squadFeedbackStore() {
        System.out.println("[SquadOS] FeedbackStore: in-process (@Improve ready)");
        return new InProcessFeedbackStore();
    }

    // ── Security (opt-in) ────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(AuditLog.class)
    @ConditionalOnProperty(prefix = "squad.security", name = "enabled", havingValue = "true")
    public AuditLog squadAuditLog() {
        System.out.println("[SquadOS] AuditLog: enabled");
        return new AuditLog();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityGuard.class)
    @ConditionalOnProperty(prefix = "squad.security", name = "enabled", havingValue = "true")
    public SecurityGuard squadSecurityGuard(AuditLog auditLog) {
        System.out.println("[SquadOS] SecurityGuard: RBAC enabled");
        return new SecurityGuard(auditLog);
    }

    @Bean
    @ConditionalOnMissingBean(JwtValidator.class)
    @ConditionalOnProperty(prefix = "squad.security", name = "enabled", havingValue = "true")
    public JwtValidator squadJwtValidator() {
        return new JwtValidator(props.getSecurity().getJwtIssuer());
    }

    // ── Rate Limiting (always active — no-op for agents without @RateLimit) ──

    @Bean
    @ConditionalOnMissingBean(RateLimitEnforcer.class)
    public RateLimitEnforcer squadRateLimitEnforcer() {
        System.out.println("[SquadOS] RateLimitEnforcer: sliding-window active");
        return new RateLimitEnforcer();
    }

    // ── Guardrails (opt-in) ──────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(GuardrailEngine.class)
    @ConditionalOnProperty(prefix = "squad.guardrails", name = "enabled", havingValue = "true")
    public GuardrailEngine squadGuardrailEngine() {
        System.out.println("[SquadOS] GuardrailEngine: safety filters active");
        return new GuardrailEngine();
    }

    // ── Durable Store (opt-in) ───────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(DurableStore.class)
    @ConditionalOnProperty(prefix = "squad.durable", name = "enabled", havingValue = "true")
    public DurableStore squadDurableStore() {
        String store = props.getDurable().getStore();
        if ("redis".equalsIgnoreCase(store)) {
            SquadProperties.Redis redis = props.getRedis();
            System.out.printf("[SquadOS] DurableStore: Redis %s:%d%n",
                redis.getHost(), redis.getPort());
            return new RedisDurableStore(
                redis.getHost(), redis.getPort(),
                redis.getPassword(), props.getDurable().getTtlHours());
        }
        System.out.println("[SquadOS] DurableStore: in-process (memory)");
        return new InProcessDurableStore();
    }

    // ── Conversation Store (opt-in) ──────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(ConversationStore.class)
    @ConditionalOnProperty(prefix = "squad.conversation", name = "enabled", havingValue = "true")
    public ConversationStore squadConversationStore() {
        System.out.printf("[SquadOS] ConversationStore: in-process (max-turns=%d)%n",
            props.getConversation().getMaxTurns());
        return new InProcessConversationStore();
    }

    // ── MCP Tool Provider (opt-in) ───────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(McpToolProvider.class)
    @ConditionalOnProperty(prefix = "squad.mcp", name = "enabled", havingValue = "true")
    public McpToolProvider squadMcpClient() {
        System.out.printf("[SquadOS] McpToolProvider: HTTP client (timeout=%dms)%n",
            props.getMcp().getTimeoutMs());
        return new HttpMcpClient(props.getMcp().getTimeoutMs());
    }

    // ── Remote Squad Injector (always active BeanPostProcessor) ──────────────

    @Bean
    @ConditionalOnMissingBean(RemoteSquadInjector.class)
    public RemoteSquadInjector squadRemoteSquadInjector() {
        System.out.println("[SquadOS] RemoteSquadInjector: @RemoteSquad field injection active");
        return new RemoteSquadInjector(props.getApi().getKey());
    }

    // ── Agent API Registrar (opt-in, requires spring-web) ────────────────────

    @Bean
    @ConditionalOnMissingBean(AgentApiRegistrar.class)
    @ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping")
    @ConditionalOnProperty(prefix = "squad.agent-api", name = "enabled", havingValue = "true")
    public AgentApiRegistrar squadAgentApiRegistrar(
            SquadContext context,
            RequestMappingHandlerMapping handlerMapping) {
        System.out.println("[SquadOS] AgentApiRegistrar: @AgentAPI HTTP routes active");
        AgentApiRegistrar registrar =
            new AgentApiRegistrar(context, handlerMapping, props.getApi().getKey());
        registrar.registerAll();
        return registrar;
    }

    // ── Embedding Adapter (opt-in — requires EmbeddingModel on classpath) ─────

    @Bean
    @ConditionalOnMissingBean(SpringAiEmbeddingAdapter.class)
    @ConditionalOnClass(name = "org.springframework.ai.embedding.EmbeddingModel")
    @ConditionalOnBean(name = "embeddingModel")
    @ConditionalOnProperty(prefix = "squad.memory", name = "enabled", havingValue = "true")
    public SpringAiEmbeddingAdapter squadEmbeddingAdapter(
            org.springframework.ai.embedding.EmbeddingModel embeddingModel) {
        System.out.println("[SquadOS] EmbeddingPort: Spring AI adapter (semantic memory active)");
        return new SpringAiEmbeddingAdapter(embeddingModel);
    }

    // ── Metrics Adapter (@Observe — opt-in via Micrometer) ────────────────────

    /**
     * In-memory MetricsPort — always registered as fallback.
     * Replaced automatically by MicrometerMetricsAdapter when Micrometer is present.
     */
    @Bean
    @ConditionalOnMissingBean(MetricsPort.class)
    public io.squados.metrics.InMemoryMetricsCollector squadInMemoryMetrics() {
        System.out.println("[SquadOS] MetricsPort: in-memory collector (@Observe ready)");
        return new io.squados.metrics.InMemoryMetricsCollector();
    }

    /**
     * Micrometer MetricsPort — registered when spring-boot-actuator (Micrometer) is on the
     * classpath and a MeterRegistry bean is present. Emits Prometheus-compatible counters
     * and timers per @Observe-annotated agent call.
     *
     * To use: add spring-boot-starter-actuator to your application pom.
     */
    @Bean
    @ConditionalOnMissingBean(MetricsPort.class)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnBean(name = "meterRegistry")
    public MicrometerMetricsAdapter squadMicrometerMetrics(
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        System.out.println("[SquadOS] MetricsPort: Micrometer adapter (Prometheus metrics active)");
        return new MicrometerMetricsAdapter(meterRegistry);
    }

    // ── Tier 2: @Benchmark ────────────────────────────────────────────────────

    /**
     * BenchmarkRunner — evaluate agent quality against golden datasets.
     * Requires SquadContext to be present.
     */
    @Bean
    @ConditionalOnMissingBean(io.squados.benchmark.BenchmarkRunner.class)
    @ConditionalOnProperty(prefix = "squad.benchmark", name = "enabled",
                           havingValue = "true", matchIfMissing = false)
    public io.squados.benchmark.BenchmarkRunner squadBenchmarkRunner(
            SquadContext squadContext, LlmPort llmPort) {
        System.out.println("[SquadOS] BenchmarkRunner: ready (@Benchmark active)");
        return new io.squados.benchmark.BenchmarkRunner(squadContext, llmPort);
    }

    // ── Tier 2: @OptimizePrompt ───────────────────────────────────────────────

    /**
     * PromptOptimizerEngine — DSPy-style automated prompt optimization.
     */
    @Bean
    @ConditionalOnMissingBean(io.squados.optimize.PromptOptimizerEngine.class)
    @ConditionalOnProperty(prefix = "squad.optimize", name = "enabled",
                           havingValue = "true", matchIfMissing = false)
    public io.squados.optimize.PromptOptimizerEngine squadPromptOptimizer(
            SquadContext squadContext, LlmPort llmPort) {
        System.out.println("[SquadOS] PromptOptimizerEngine: ready (@OptimizePrompt active)");
        return new io.squados.optimize.PromptOptimizerEngine(squadContext, llmPort);
    }

    /**
     * PromptVersionStore — in-process history of optimized prompt versions.
     * Always created when optimize is enabled; shared between PromptOptimizerEngine instances.
     */
    @Bean
    @ConditionalOnMissingBean(io.squados.optimize.PromptVersionStore.class)
    @ConditionalOnProperty(prefix = "squad.optimize", name = "enabled",
                           havingValue = "true", matchIfMissing = false)
    public io.squados.optimize.PromptVersionStore squadPromptVersionStore() {
        return new io.squados.optimize.PromptVersionStore();
    }

    // ── Tier 2: @Debate ───────────────────────────────────────────────────────

    /**
     * DebateEngine — multi-agent debate protocol with convergence detection and voting.
     */
    @Bean
    @ConditionalOnMissingBean(io.squados.debate.DebateEngine.class)
    @ConditionalOnProperty(prefix = "squad.debate", name = "enabled",
                           havingValue = "true", matchIfMissing = false)
    public io.squados.debate.DebateEngine squadDebateEngine(
            SquadContext squadContext,
            LlmPort llmPort) {
        System.out.println("[SquadOS] DebateEngine: ready (@Debate active)");
        return new io.squados.debate.DebateEngine(squadContext.getRegistry(), llmPort);
    }

    // ── Tier 2: OtelSpanExporter ──────────────────────────────────────────────

    /**
     * OtelSpanExporter — async OTLP/HTTP trace export to Jaeger, Grafana Tempo, etc.
     * Activated by squad.otel.enabled=true; endpoint configured via squad.otel.endpoint
     * or the standard OTEL_EXPORTER_OTLP_ENDPOINT environment variable.
     */
    @Bean
    @ConditionalOnMissingBean(io.squados.otel.OtelSpanExporter.class)
    @ConditionalOnProperty(prefix = "squad.otel", name = "enabled", havingValue = "true")
    public io.squados.otel.OtelSpanExporter squadOtelExporter(SquadProperties props) {
        String endpoint = props.getOtel().getEndpoint();
        String service  = props.getOtel().getServiceName();
        io.squados.otel.OtelExporterConfig config = io.squados.otel.OtelExporterConfig.builder()
            .endpoint(endpoint != null ? endpoint : io.squados.otel.OtelExporterConfig.fromEnv().endpoint())
            .serviceName(service != null ? service : io.squados.otel.OtelExporterConfig.fromEnv().serviceName())
            .timeoutMs(props.getOtel().getTimeoutMs())
            .build();
        System.out.println("[SquadOS] OtelSpanExporter: exporting to " + config.traceEndpoint());
        return new io.squados.otel.OtelSpanExporter(config);
    }

    // ── Tier 3: squad-mcp-server ──────────────────────────────────────────────

    /**
     * McpServer — exposes @Agent classes as MCP tools for Claude Desktop and MCP clients.
     * Activated by squad.mcp.server.enabled=true.
     * Configure port via squad.mcp.server.port (default: 3000).
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean(io.squados.mcp.server.McpServer.class)
    @ConditionalOnClass(name = "io.squados.mcp.server.McpServer")
    @ConditionalOnProperty(prefix = "squad.mcp.server", name = "enabled", havingValue = "true")
    public io.squados.mcp.server.McpServer squadMcpServer(
            SquadContext squadContext,
            LlmPort llmPort,
            SquadProperties props) {
        int port = props.getMcp().getServerPort();
        io.squados.mcp.server.McpServerConfig config =
            io.squados.mcp.server.McpServerConfig.builder()
                .port(port)
                .serverName("squad-mcp-server")
                .build();
        java.util.List<Class<?>> agentClasses = squadContext.getRegistry().all().stream()
            .map(io.squados.context.AgentWrapper::getAgentClass)
            .collect(java.util.stream.Collectors.toList());
        try {
            io.squados.mcp.server.McpServer server =
                io.squados.mcp.server.McpServer.start(config, agentClasses, llmPort);
            System.out.println("[SquadOS] McpServer: listening on port " + server.port()
                + " (" + agentClasses.size() + " agent tools)");
            return server;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to start McpServer on port " + port, e);
        }
    }
}
