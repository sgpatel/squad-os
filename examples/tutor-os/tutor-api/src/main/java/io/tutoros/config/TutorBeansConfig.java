package io.tutoros.config;

import io.squados.context.SquadContext;
import io.squados.debate.DebateEngine;
import io.squados.memory.MemoryManager;
import io.tutoros.agent.*;
import io.tutoros.mastery.InProcessMasteryGraphStore;
import io.tutoros.mastery.JdbcMasteryGraphStore;
import io.tutoros.mastery.MasteryGraphStore;
import io.tutoros.mastery.MasteryService;
import io.tutoros.pipeline.*;
import io.tutoros.pipeline.PipelineEventBus;
import io.tutoros.websocket.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Spring @Configuration that wires all TutorOS beans.
 *
 * Agents are plain Java classes annotated with @Agent — they are not Spring
 * @Component-annotated by design so that SquadOS (not Spring DI) manages their
 * lifecycle via AgentRegistry.  We declare them here as @Beans so that:
 *
 *   1. Spring can inject them into TutoringPipeline and SessionManager.
 *   2. SquadOS auto-configuration can pick them up from the ApplicationContext
 *      and register them in AgentRegistry at startup (@SquadApplication trigger).
 *   3. @RemoteSquad proxy injection happens inside SquadOS's BeanPostProcessor
 *      before any bean is used.
 *
 * Order matters: agents first, pipeline second, manager last.
 */
@Configuration
public class TutorBeansConfig {

    // ── Agents ────────────────────────────────────────────────────────────────

    @Bean
    public GuardianAgent guardianAgent() {
        return new GuardianAgent();
    }

    @Bean
    public DiagnosticAgent diagnosticAgent() {
        return new DiagnosticAgent();
    }

    @Bean
    public CurriculumPlannerAgent curriculumPlannerAgent() {
        return new CurriculumPlannerAgent();
    }

    @Bean
    public ContentAgent contentAgent() {
        return new ContentAgent();
    }

    @Bean
    public IntentAnalyzerAgent intentAnalyzerAgent() {
        return new IntentAnalyzerAgent();
    }

    @Bean
    public SocraticTutorAgent socraticTutorAgent() {
        return new SocraticTutorAgent();
    }

    @Bean
    public DirectTutorAgent directTutorAgent() {
        return new DirectTutorAgent();
    }

    @Bean
    public PracticeAgent practiceAgent() {
        return new PracticeAgent();
    }

    @Bean
    public AssessmentAgent assessmentAgent() {
        return new AssessmentAgent();
    }

    @Bean
    public ProgressAgent progressAgent() {
        return new ProgressAgent();
    }

    @Bean
    public EscalationAgent escalationAgent() {
        return new EscalationAgent();
    }

    @Bean
    public VisualisationAgent visualisationAgent() {
        return new VisualisationAgent();
    }

    @Bean
    public TodoAgent todoAgent() {
        return new TodoAgent();
    }

    @Bean
    public QuizAgent quizAgent() {
        return new QuizAgent();
    }

    @Bean
    public SyllabusSuggesterAgent syllabusSuggesterAgent() {
        return new SyllabusSuggesterAgent();
    }

    // ── Mastery (M3-A) ────────────────────────────────────────────────
    /**
     * MasteryGraphStore — backend selection driven by {@code tutor.mastery.store}.
     *
     *   in-process (default) → ephemeral, in-memory map. Zero-config.
     *   jdbc                 → {@link JdbcMasteryGraphStore} bound to the
     *                          available {@link DataSource}. Survives restarts.
     *
     * Falls back to in-process if {@code jdbc} is requested but no
     * DataSource bean exists, so dev / CI without a database doesn't
     * break — same forgiving pattern as M1's {@code MemoryConfig} for
     * pgvector episodic memory.
     */
    @Bean
    @ConditionalOnMissingBean
    public MasteryGraphStore masteryGraphStore(
            @Value("${tutor.mastery.store:in-process}") String backend,
            ObjectProvider<DataSource> dataSourceProvider) {
        String b = backend == null ? "in-process" : backend.trim().toLowerCase();
        if ("jdbc".equals(b)) {
            DataSource ds = dataSourceProvider.getIfAvailable();
            if (ds == null) {
                System.err.println(
                    "[TutorOS] tutor.mastery.store=jdbc but no DataSource bean — " +
                    "falling back to in-process. Add spring.datasource.* config to " +
                    "enable persistent mastery.");
                return new InProcessMasteryGraphStore();
            }
            try {
                System.out.println("[TutorOS] MasteryGraphStore: jdbc (persistent)");
                return new JdbcMasteryGraphStore(ds);
            } catch (RuntimeException init) {
                System.err.println(
                    "[TutorOS] JdbcMasteryGraphStore init failed (" + init.getMessage() +
                    ") — falling back to in-process.");
                return new InProcessMasteryGraphStore();
            }
        }
        System.out.println("[TutorOS] MasteryGraphStore: in-process (ephemeral)");
        return new InProcessMasteryGraphStore();
    }

    /** Owns SM-2 + score updates + due-queue resolution. */
    @Bean
    public MasteryService masteryService(MasteryGraphStore store) {
        return new MasteryService(store);
    }

    // ── Pipeline ──────────────────────────────────────────────────────────────

    /**
     * TutoringPipeline — the 10-step @Pipeline that routes each learner
     * message through: Guardian → Diagnostic/Plan/Continue → Content →
     * Debate → Tutor → Practice → Assessment → Progress → Escalation →
     * Visualisation → Todo/Quiz generation.
     */
    @Bean
    public TutoringPipeline tutoringPipeline(
            SquadContext ctx,
            GuardianAgent guardianAgent,
            DiagnosticAgent diagnosticAgent,
            CurriculumPlannerAgent curriculumPlannerAgent,
            ContentAgent contentAgent,
            IntentAnalyzerAgent intentAnalyzerAgent,
            SocraticTutorAgent socraticTutorAgent,
            DirectTutorAgent directTutorAgent,
            PracticeAgent practiceAgent,
            AssessmentAgent assessmentAgent,
            ProgressAgent progressAgent,
            EscalationAgent escalationAgent,
            VisualisationAgent visualisationAgent,
            TodoAgent todoAgent,
            QuizAgent quizAgent,
            // DebateEngine is auto-registered by squad-spring-boot-starter
            // (SquadAutoConfiguration#squadDebateEngine, @ConditionalOnMissingBean).
            DebateEngine debateEngine,
            // PipelineEventBus is wired below; declared here so Spring
            // resolves it before constructing the pipeline.
            PipelineEventBus pipelineEventBus,
            // MemoryManager is created by MemoryConfig only when
            // squad.memory.enabled=true. ObjectProvider keeps the
            // pipeline runnable when memory is turned off — the write
            // call short-circuits cleanly on null.
            ObjectProvider<MemoryManager> memoryManagerProvider) {

        return new TutoringPipeline(
            ctx,
            guardianAgent, diagnosticAgent, curriculumPlannerAgent,
            contentAgent, intentAnalyzerAgent, socraticTutorAgent, directTutorAgent,
            practiceAgent, assessmentAgent, progressAgent,
            escalationAgent,
            todoAgent, quizAgent, visualisationAgent,
            debateEngine,
            pipelineEventBus,
            memoryManagerProvider.getIfAvailable()
        );
    }

    /**
     * Bridges {@link PipelineEventBus} → WebSocket frames so connected UI
     * clients see per-stage progress in real time. Distinct bean from the
     * handler so unit tests can swap in a recording bus.
     */
    @Bean
    public PipelineEventBus pipelineEventBus(TutoringWebSocketHandler wsHandler) {
        return new WebSocketPipelineEventBus(wsHandler);
    }

    // ── WebSocket ─────────────────────────────────────────────────────────────

    /**
     * TutoringWebSocketHandler — Spring @Component (via @Component on the class
     * itself) but declared here for explicit visibility.  It is the hub that
     * receives broadcastToken() calls from WebSocketTokenWriter.
     */
    @Bean
    public TutoringWebSocketHandler tutoringWebSocketHandler() {
        return new TutoringWebSocketHandler();
    }

    /**
     * WebSocketTokenWriter — implements SquadOS TokenWriter interface.
     * Passed to DirectTutorAgent via @Streaming(writer=WebSocketTokenWriter.class).
     * Bridges LLM token stream → WebSocket broadcast.
     */
    @Bean
    public WebSocketTokenWriter webSocketTokenWriter(TutoringWebSocketHandler wsHandler) {
        return new WebSocketTokenWriter(wsHandler);
    }

    // ── Session manager ───────────────────────────────────────────────────────

    /**
     * SessionManager — singleton session lifecycle controller.
     * Delegates all agent invocations to TutoringPipeline.
     */
    @Bean
    public SessionManager sessionManager(TutoringPipeline pipeline, SquadContext ctx) {
        return new SessionManager(pipeline, ctx);
    }
}
