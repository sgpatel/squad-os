package io.squados.boot;

import io.squados.approval.InProcessApprovalStore;
import io.squados.config.SquadConfigBridge;
import io.squados.context.SquadContext;
import io.squados.context.SquadRunner;
import io.squados.event.InProcessEventBus;
import io.squados.improve.InProcessFeedbackStore;
import io.squados.llm.LlmPort;
import io.squados.security.AuditLog;
import io.squados.security.JwtValidator;
import io.squados.security.SecurityGuard;
import io.squados.trace.InMemoryTraceExporter;
import io.squados.trace.LogTraceExporter;
import io.squados.trace.SquadTracer;
import io.squados.trace.TraceExporter;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot Auto-Configuration for SquadOS.
 *
 * Automatically configures all required beans when squad-spring-boot-starter
 * is on the classpath. Zero boilerplate required.
 *
 * Beans created:
 *   - LlmPort          (Spring AI adapter — requires spring-ai-starter-model-ollama)
 *   - SquadContext      (main entry point)
 *   - TraceExporter     (log by default, memory if squad.tracing.exporter=memory)
 *   - InProcessApprovalStore
 *   - InProcessEventBus
 *   - InProcessFeedbackStore
 *   - AuditLog + SecurityGuard (if squad.security.enabled=true)
 *   - JwtValidator      (if squad.security.enabled=true)
 *
 * All beans use {@code @ConditionalOnMissingBean} — override any by declaring
 * your own {@code @Bean} in your {@code @SpringBootApplication} class.
 *
 * Usage — before (50 lines of boilerplate):
 * <pre>
 * {@literal @}Bean LlmPort llmPort(ChatClient.Builder b) { return new SpringAiLlmAdapter(b); }
 * {@literal @}Bean SquadContext ctx(LlmPort llm) { return SquadRunner.run(App.class, llm); }
 * {@literal @}Bean InMemoryTraceExporter tracer() { ... }
 * {@literal @}Bean InProcessApprovalStore approvals() { ... }
 * // ... 40 more lines
 * </pre>
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
        // Prevent Spring AI reactive deps from starting a web server
        System.setProperty("spring.main.web-application-type", "none");
    }

    // ── LLM Port ────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(LlmPort.class)
    public LlmPort squadLlmPort(ChatClient.Builder builder) {
        System.out.printf("[SquadOS] LlmPort: Spring AI %s/%s%n",
            props.getLlm().getProvider(), props.getLlm().getModel());
        return new SpringAiLlmAdapter(builder);
    }

    // ── Squad Context ────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(SquadContext.class)
    public SquadContext squadContext(LlmPort llmPort) {
        System.out.printf("[SquadOS] Booting squad: %s%n", props.getName());
        // Find the @SquadApplication annotated class on the stack
        Class<?> appClass = findSquadApplicationClass();
        return SquadRunner.run(appClass, llmPort);
    }

    // ── Tracing ──────────────────────────────────────────────────

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

    // ── Approval ─────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(InProcessApprovalStore.class)
    @ConditionalOnProperty(prefix = "squad.approval", name = "enabled",
                           havingValue = "true", matchIfMissing = true)
    public InProcessApprovalStore squadApprovalStore() {
        System.out.println("[SquadOS] ApprovalStore: in-process");
        return new InProcessApprovalStore();
    }

    // ── Event Bus ────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(InProcessEventBus.class)
    public InProcessEventBus squadEventBus() {
        System.out.println("[SquadOS] EventBus: in-process");
        return new InProcessEventBus();
    }

    // ── Feedback Store ───────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(InProcessFeedbackStore.class)
    public InProcessFeedbackStore squadFeedbackStore() {
        System.out.println("[SquadOS] FeedbackStore: in-process (@Improve ready)");
        return new InProcessFeedbackStore();
    }

    // ── Security (opt-in) ────────────────────────────────────────

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

    // ── Helpers ──────────────────────────────────────────────────

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