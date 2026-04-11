package io.squados.boot;

import io.squados.approval.InProcessApprovalStore;
import io.squados.config.SquadConfigBridge;
import io.squados.context.SquadContext;
import io.squados.context.SquadRunner;
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
import io.squados.ratelimit.RateLimitEnforcer;
import io.squados.security.AuditLog;
import io.squados.security.JwtValidator;
import io.squados.security.SecurityGuard;
import io.squados.trace.InMemoryTraceExporter;
import io.squados.trace.LogTraceExporter;
import io.squados.trace.SquadTracer;
import io.squados.trace.TraceExporter;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Spring Boot Auto-Configuration for SquadOS.
 *
 * Automatically configures all required beans when squad-spring-boot-starter
 * is on the classpath. Zero boilerplate required.
 *
 * Beans created:
 *   - LlmPort                    (Spring AI adapter — requires spring-ai-starter-model-ollama)
 *   - SquadContext                (main entry point)
 *   - TraceExporter               (log by default, memory if squad.tracing.exporter=memory)
 *   - InProcessApprovalStore
 *   - InProcessEventBus
 *   - InProcessFeedbackStore
 *   - AuditLog + SecurityGuard    (if squad.security.enabled=true)
 *   - JwtValidator                (if squad.security.enabled=true)
 *   - RateLimitEnforcer           (always — agents without @RateLimit are unaffected)
 *   - GuardrailEngine             (if squad.guardrails.enabled=true)
 *   - InProcessDurableStore       (if squad.durable.enabled=true, store=memory)
 *   - RedisDurableStore           (if squad.durable.enabled=true, store=redis)
 *   - InProcessConversationStore  (if squad.conversation.enabled=true)
 *   - HttpMcpClient               (if squad.mcp.enabled=true)
 *   - RemoteSquadInjector         (always — BeanPostProcessor, no-op when no @RemoteSquad fields)
 *   - AgentApiRegistrar           (if squad.agent-api.enabled=true)
 *   - SpringAiEmbeddingAdapter    (if squad.memory.enabled=true and EmbeddingModel present)
 *
 * All beans use {@code @ConditionalOnMissingBean} — override any by declaring
 * your own {@code @Bean} in your {@code @SpringBootApplication} class.
 *
 * Usage — after (zero boilerplate):
 * <pre>
 * {@literal @}SpringBootApplication
 * {@literal @}SquadApplication
 * public class MyApp {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApp.class, args);
 *     }
 * }
 * </pre>
 */
@AutoConfiguration
@EnableConfigurationProperties(SquadProperties.class)
public class SquadAutoConfiguration {

    private final SquadProperties props;

    public SquadAutoConfiguration(SquadProperties props) {
        this.props = props;
        SquadConfigBridge.applyToSystemProperties();
    }

    // ── LLM Port ─────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(LlmPort.class)
    public LlmPort squadLlmPort(ChatClient.Builder builder) {
        System.out.printf("[SquadOS] LlmPort: Spring AI %s/%s%n",
            props.getLlm().getProvider(), props.getLlm().getModel());
        return new SpringAiLlmAdapter(builder);
    }

    // ── Squad Context ─────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(SquadContext.class)
    public SquadContext squadContext(LlmPort llmPort) {
        System.out.printf("[SquadOS] Booting squad: %s%n", props.getName());
        Class<?> appClass = findSquadApplicationClass();
        return SquadRunner.run(appClass, llmPort);
    }

    // ── Tracing ───────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(TraceExporter.class)
    @ConditionalOnProperty(prefix = "squad.tracing", name = "enabled",
                           havingValue = "true", matchIfMissing = true)
    public TraceExporter squadTraceExporter() {
        String exporterType = props.getTracing().getExporter();
        TraceExporter exporter = switch (exporterType) {
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

    // ── Approval ──────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(InProcessApprovalStore.class)
    @ConditionalOnProperty(prefix = "squad.approval", name = "enabled",
                           havingValue = "true", matchIfMissing = true)
    public InProcessApprovalStore squadApprovalStore() {
        System.out.println("[SquadOS] ApprovalStore: in-process");
        return new InProcessApprovalStore();
    }

    // ── Event Bus ─────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(InProcessEventBus.class)
    public InProcessEventBus squadEventBus() {
        System.out.println("[SquadOS] EventBus: in-process");
        return new InProcessEventBus();
    }

    // ── Feedback Store ────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(InProcessFeedbackStore.class)
    public InProcessFeedbackStore squadFeedbackStore() {
        System.out.println("[SquadOS] FeedbackStore: in-process (@Improve ready)");
        return new InProcessFeedbackStore();
    }

    // ── Security (opt-in) ─────────────────────────────────────────────────

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

    // ── Rate Limiting ─────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(RateLimitEnforcer.class)
    public RateLimitEnforcer squadRateLimitEnforcer() {
        System.out.println("[SquadOS] RateLimitEnforcer: sliding-window enabled");
        return new RateLimitEnforcer();
    }

    // ── Guardrails (opt-in) ───────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(GuardrailEngine.class)
    @ConditionalOnProperty(prefix = "squad.guardrails", name = "enabled", havingValue = "true")
    public GuardrailEngine squadGuardrailEngine() {
        System.out.println("[SquadOS] GuardrailEngine: safety filters active");
        return new GuardrailEngine();
    }

    // ── Durable Workflow Store (opt-in) ───────────────────────────────────

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

    // ── Conversation Store (opt-in) ───────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(ConversationStore.class)
    @ConditionalOnProperty(prefix = "squad.conversation", name = "enabled", havingValue = "true")
    public ConversationStore squadConversationStore() {
        System.out.printf("[SquadOS] ConversationStore: in-process (max-turns=%d)%n",
            props.getConversation().getMaxTurns());
        return new InProcessConversationStore();
    }

    // ── MCP Tool Provider (opt-in) ────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(McpToolProvider.class)
    @ConditionalOnProperty(prefix = "squad.mcp", name = "enabled", havingValue = "true")
    public McpToolProvider squadMcpClient() {
        System.out.printf("[SquadOS] McpToolProvider: HTTP client (timeout=%dms)%n",
            props.getMcp().getTimeoutMs());
        return new HttpMcpClient(props.getMcp().getTimeoutMs());
    }

    // ── Remote Squad Injector (BeanPostProcessor) ─────────────────────────

    @Bean
    @ConditionalOnMissingBean(RemoteSquadInjector.class)
    public RemoteSquadInjector squadRemoteSquadInjector() {
        System.out.println("[SquadOS] RemoteSquadInjector: @RemoteSquad field injection ready");
        return new RemoteSquadInjector(props.getApi().getKey());
    }

    // ── Agent API HTTP Registrar (opt-in) ─────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(AgentApiRegistrar.class)
    @ConditionalOnProperty(prefix = "squad.agent-api", name = "enabled", havingValue = "true")
    public AgentApiRegistrar squadAgentApiRegistrar(
            SquadContext context,
            RequestMappingHandlerMapping handlerMapping) {
        System.out.println("[SquadOS] AgentApiRegistrar: dynamic @AgentAPI routes enabled");
        AgentApiRegistrar registrar =
            new AgentApiRegistrar(context, handlerMapping, props.getApi().getKey());
        registrar.registerAll();
        return registrar;
    }

    // ── Embedding Adapter (opt-in, requires EmbeddingModel on classpath) ──

    @Bean
    @ConditionalOnMissingBean(SpringAiEmbeddingAdapter.class)
    @ConditionalOnBean(EmbeddingModel.class)
    @ConditionalOnProperty(prefix = "squad.memory", name = "enabled", havingValue = "true")
    public SpringAiEmbeddingAdapter squadEmbeddingAdapter(EmbeddingModel embeddingModel) {
        System.out.println("[SquadOS] EmbeddingPort: Spring AI adapter (semantic memory enabled)");
        return new SpringAiEmbeddingAdapter(embeddingModel);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Walk the call stack to find the class annotated with @SquadApplication.
     * Falls back to a placeholder class if not found.
     */
    private Class<?> findSquadApplicationClass() {
        try {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (StackTraceElement el : stack) {
                try {
                    Class<?> cls = Class.forName(el.getClassName());
                    if (cls.isAnnotationPresent(io.squados.annotation.SquadApplication.class)) {
                        return cls;
                    }
                } catch (ClassNotFoundException ignored) {}
            }
        } catch (Exception ignored) {}
        return SquadAutoConfiguration.class; // safe fallback
    }
}
