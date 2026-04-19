package io.tutoros.pipeline;

import io.squados.annotation.*;
import io.squados.context.SquadContext;
import io.squados.agent.AgentResponse;
import io.squados.debate.DebateEngine;
import io.squados.debate.DebateResult;
import io.tutoros.agent.*;
import io.tutoros.model.*;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * TutoringPipeline — the central orchestrator of TutorOS.
 *
 * Every student message flows through this pipeline in order:
 *
 *   Step 1 — GuardianAgent:         Safety & compliance check (input guardrail)
 *   Step 2 — ProgressAgent:         Load session state + @AgentMemory recall
 *   Step 3 — Routing decision:      First session? → DiagnosticAgent
 *                                   New subject?   → CurriculumPlannerAgent
 *                                   Continuation   → resume at current chapter
 *   Step 4 — ContentAgent:          Fetch grounded examples via @SquadTool
 *   Step 5 — @Debate:               SocraticTutor vs DirectTutor → winner teaches
 *   Step 6 — Winning tutor:         Stream explanation to student (@Streaming)
 *   Step 7 — PracticeAgent:         Generate calibrated practice question
 *   Step 8 — AssessmentAgent:       Grade student answer, produce feedback
 *   Step 9 — ProgressAgent:         Update mastery, detect plateau/acceleration
 *   Step 10 — EscalationAgent:      If stuck/distressed → @AwaitApproval
 *   Step 11 — GuardianAgent:        Output guardrail check
 *   Step 12 — Session end:          SummaryAgent → TodoAgent → checkpoint
 *
 * Features used by this class:
 *   @Pipeline    — declares the multi-step agent sequence to SquadOS
 *   @DurableAgent— the entire pipeline state is checkpointed after each step
 *   @Traced      — root span wrapping all 12 steps
 *   @Debate      — DebateEngine wired for SocraticTutor vs DirectTutor at step 5
 */
@Agent(
    role        = AgentRole.STRATEGIST,
    name        = "TutoringPipeline",
    description = "Root orchestrator. Routes every student message through " +
                  "the full 12-step tutoring pipeline from safety check to " +
                  "session checkpoint."
)
@Pipeline(
    name     = "tutoring-session",
    failFast = false, // if one step fails, attempt graceful degradation
    steps    = {
        @Step(role = AgentRole.SUPPORT,     name = "guardian"),
        @Step(role = AgentRole.SUPPORT,     name = "progress-load"),
        @Step(role = AgentRole.RESEARCHER,  name = "content-fetch"),
        @Step(role = AgentRole.CRITIC,      name = "socratic-tutor"),
        @Step(role = AgentRole.WRITER,      name = "direct-tutor"),
        @Step(role = AgentRole.EXECUTOR,    name = "practice"),
        @Step(role = AgentRole.CRITIC,      name = "assessment"),
        @Step(role = AgentRole.SUPPORT,     name = "progress-update"),
        @Step(role = AgentRole.SUPPORT,     name = "escalation",
              condition = "response.contains('ESCALATE')"),
        @Step(role = AgentRole.EXECUTOR,    name = "summary",
              condition = "session.ended"),
    }
)
@DurableAgent(store = "memory", ttlHours = 72)
@Traced(spanName = "tutoring-pipeline-root", trackTokens = true)
public class TutoringPipeline {

    private static final Logger log = System.getLogger(TutoringPipeline.class.getName());

    // All agents are injected by SquadContext at boot
    private final SquadContext      ctx;
    private final GuardianAgent     guardian;
    private final DiagnosticAgent   diagnostic;
    private final CurriculumPlannerAgent planner;
    private final ContentAgent      content;
    private final SocraticTutorAgent socratic;
    private final DirectTutorAgent  direct;
    private final PracticeAgent     practice;
    private final AssessmentAgent   assessment;
    private final ProgressAgent     progress;
    private final EscalationAgent   escalation;
    private final TodoAgent         todo;
    private final QuizAgent         quiz;
    private final VisualisationAgent visualisation;
    private final DebateEngine      debateEngine;
    /**
     * Streams per-stage progress to interested observers (WebSocket clients,
     * tracing exporters, audit logs). Defaults to a no-op so the pipeline
     * remains runnable in tests and CLI contexts without any transport.
     */
    private final PipelineEventBus  events;

    public TutoringPipeline(SquadContext ctx,
                             GuardianAgent guardian,
                             DiagnosticAgent diagnostic,
                             CurriculumPlannerAgent planner,
                             ContentAgent content,
                             SocraticTutorAgent socratic,
                             DirectTutorAgent direct,
                             PracticeAgent practice,
                             AssessmentAgent assessment,
                             ProgressAgent progress,
                             EscalationAgent escalation,
                             TodoAgent todo,
                             QuizAgent quiz,
                             VisualisationAgent visualisation,
                             DebateEngine debateEngine) {
        this(ctx, guardian, diagnostic, planner, content, socratic, direct,
             practice, assessment, progress, escalation, todo, quiz,
             visualisation, debateEngine, PipelineEventBus.NOOP);
    }

    /**
     * Full constructor with explicit event bus. Use this in production when
     * a WebSocket-backed bus should observe stage transitions.
     */
    public TutoringPipeline(SquadContext ctx,
                             GuardianAgent guardian,
                             DiagnosticAgent diagnostic,
                             CurriculumPlannerAgent planner,
                             ContentAgent content,
                             SocraticTutorAgent socratic,
                             DirectTutorAgent direct,
                             PracticeAgent practice,
                             AssessmentAgent assessment,
                             ProgressAgent progress,
                             EscalationAgent escalation,
                             TodoAgent todo,
                             QuizAgent quiz,
                             VisualisationAgent visualisation,
                             DebateEngine debateEngine,
                             PipelineEventBus events) {
        this.ctx           = ctx;
        this.guardian      = guardian;
        this.diagnostic    = diagnostic;
        this.planner       = planner;
        this.content       = content;
        this.socratic      = socratic;
        this.direct        = direct;
        this.practice      = practice;
        this.assessment    = assessment;
        this.progress      = progress;
        this.escalation    = escalation;
        this.todo          = todo;
        this.quiz          = quiz;
        this.visualisation = visualisation;
        this.debateEngine  = debateEngine;
        this.events        = events != null ? events : PipelineEventBus.NOOP;
    }

    /**
     * Entry point — called by SessionController for every student message.
     *
     * @param session current session state (learner profile, chapter, history)
     * @param message raw student message text
     * @return PipelineResult containing the tutor response + metadata
     */
    public PipelineResult process(SessionState session, String message) {
        final String sid = session.sessionId();
        try {
            // ── Step 1: Guardian input check ────────────────────────────
            events.stageStart(sid, "guardian");
            String verdict = guardianVerdict(sid, message, session.profile().level(), "agentic");

            if ("DISTRESS".equals(verdict)) {
                notifyEscalation(session, message, true);
                events.done(sid);
                return PipelineResult.safe(guardian.distressResponse(session.profile().name()));
            }
            if ("DISHONESTY".equals(verdict)) {
                events.done(sid);
                return PipelineResult.safe(guardian.dishonestyResponse());
            }
            if ("INJECTION".equals(verdict) || "INAPPROPRIATE".equals(verdict)) {
                events.done(sid);
                return PipelineResult.blocked("Message blocked by safety filter.");
            }

            // ── Step 2: Route — diagnose / plan / continue ───────────────
            if (session.isFirstSession()) {
                events.stageStart(sid, "diagnostic");
                PipelineResult r = runDiagnosticFlow(session, message);
                events.stageDone(sid, "diagnostic", null);
                events.done(sid);
                return r;
            }
            if (session.needsNewPlan()) {
                events.stageStart(sid, "planner");
                PipelineResult r = runPlanningFlow(session, message);
                events.stageDone(sid, "planner", null);
                events.done(sid);
                return r;
            }

            // ── Step 3: Content fetch (tool calls) ───────────────────────
            events.stageStart(sid, "content");
            AgentResponse contentResult = ctx.submitTo(AgentRole.RESEARCHER,
                buildContentPrompt(session, message)
            );
            String groundedContent = contentResult.content();
            events.stageDone(sid, "content", groundedContent);

            // ── Step 4: Teaching style debate ────────────────────────────
            events.stageStart(sid, "debate");
            String winnerStyle = runTeachingDebate(session, message, groundedContent);
            events.stageDone(sid, "debate", winnerStyle);

            // ── Step 5: Tutor responds (tokens stream over WS via @Streaming) ─
            events.stageStart(sid, "tutor");
            AgentResponse tutorResult = runSelectedTutor(
                winnerStyle, session, message, groundedContent
            );
            String tutorResponse = tutorResult.content();

            // ── Step 6: Guardian output check ────────────────────────────
            AgentResponse outputCheck = ctx.submitTo(AgentRole.SUPPORT,
                guardian.outputCheckPrompt(tutorResponse, session.profile().level())
            );
            if (outputCheck.content().startsWith("FAIL")) {
                // Re-run direct tutor with a corrective instruction
                tutorResult = ctx.submitTo(AgentRole.WRITER,
                    tutorResponse + "\n\nFix: " + outputCheck.content());
                tutorResponse = tutorResult.content();
            }
            events.stageDone(sid, "tutor", null);

            // Emit a final structured MESSAGE so non-token-stream subscribers
            // (and the UI's frame translator) get the full reply with metadata
            // even on backends that don't stream tokens.
            events.message(sid, java.util.Map.of(
                "body",          tutorResponse,
                "teachingStyle", winnerStyle
            ));
            events.done(sid);

            return PipelineResult.tutor(tutorResponse, winnerStyle, groundedContent);
        } catch (RuntimeException ex) {
            events.stageError(sid, "tutor", ex.getMessage() != null ? ex.getMessage() : ex.toString());
            events.done(sid);
            throw ex;
        }
    }

    /**
     * processDirect — slim, single-agent response path.
     *
     * Runs exactly three logical steps:
     *   1. Guardian input check   (safety guardrail, no heavy classification)
     *   2. DirectTutorAgent        (streamed tokens over WS via @Streaming)
     *   3. Guardian output check   (strips / rewrites on FAIL)
     *
     * Explicitly skipped: diagnostic, curriculum planner, content fetch,
     * teaching-style debate, practice/assessment/progress hooks. Use this
     * path for follow-up clarifications or when the learner wants a quick
     * answer without pipeline ceremony.
     *
     * The stage events emitted here (STAGE_START/DONE for "guardian" and
     * "tutor") are a strict subset of process() so the UI's FrameTranslator
     * needs no mode awareness — unsent stages simply stay 'pending' and are
     * auto-finalized when DONE arrives.
     */
    public PipelineResult processDirect(SessionState session, String message) {
        final String sid = session.sessionId();
        try {
            // ── Step 1: Guardian input check ────────────────────────────
            events.stageStart(sid, "guardian");
            String verdict = guardianVerdict(sid, message, session.profile().level(), "direct");

            if ("DISTRESS".equals(verdict)) {
                notifyEscalation(session, message, true);
                events.done(sid);
                return PipelineResult.safe(guardian.distressResponse(session.profile().name()));
            }
            if ("DISHONESTY".equals(verdict)) {
                events.done(sid);
                return PipelineResult.safe(guardian.dishonestyResponse());
            }
            if ("INJECTION".equals(verdict) || "INAPPROPRIATE".equals(verdict)) {
                events.done(sid);
                return PipelineResult.blocked("Message blocked by safety filter.");
            }

            // ── Step 2: Direct tutor (streamed) ─────────────────────────
            events.stageStart(sid, "tutor");
            AgentResponse tutorResult = ctx.submitTo(AgentRole.WRITER,
                direct.teachingPrompt(
                    message, nullSafe(session.currentConcept(), "the current topic"),
                    nullSafe(session.profile().level(), "higher-sec"),
                    nullSafe(session.profile().goal(), "DEEP_UNDERSTANDING"),
                    nullSafe(session.profile().bloomsLevel(), "UNDERSTAND"),
                    nullSafe(session.profile().learningStyle(), "DIRECT"),
                    nullSafe(session.profile().analogyDomain(), "everyday life"),
                    nullSafe(session.profile().profession(), ""),
                    "", // no grounded content in direct mode
                    nullSafe(session.memoryContext(), "")
                )
            );
            String tutorResponse = nullSafe(tutorResult != null ? tutorResult.content() : null,
                "I couldn't generate a response just now — please try again.");

            // ── Step 3: Guardian output check ────────────────────────────
            AgentResponse outputCheck = ctx.submitTo(AgentRole.SUPPORT,
                guardian.outputCheckPrompt(tutorResponse, session.profile().level())
            );
            String outputVerdict = nullSafe(outputCheck != null ? outputCheck.content() : null, "PASS");
            if (outputVerdict.startsWith("FAIL")) {
                tutorResult = ctx.submitTo(AgentRole.WRITER,
                    tutorResponse + "\n\nFix: " + outputVerdict);
                tutorResponse = nullSafe(tutorResult != null ? tutorResult.content() : null, tutorResponse);
            }
            events.stageDone(sid, "tutor", null);

            events.message(sid, java.util.Map.of(
                "body",          tutorResponse,
                "teachingStyle", "DIRECT"
            ));
            events.done(sid);

            return PipelineResult.tutor(tutorResponse, "DIRECT", "");
        } catch (RuntimeException ex) {
            events.stageError(sid, "tutor", ex.getMessage() != null ? ex.getMessage() : ex.toString());
            events.done(sid);
            throw ex;
        }
    }

    /**
     * Runs a practice question cycle — generate → student answers → assess.
     * Called by SessionController when student submits a practice answer.
     */
    public PipelineResult assess(SessionState session, String studentAnswer,
                                  PracticeQuestion question, int attemptNumber) {

        AgentResponse feedbackResult = ctx.submitTo(AgentRole.CRITIC,
            assessment.gradingPrompt(
                question, studentAnswer, attemptNumber,
                session.profile().level(), session.profile().goal(),
                session.memoryContext()
            )
        );

        // Update mastery via ProgressAgent
        ctx.submitTo(AgentRole.SUPPORT,
            progress.masteryUpdatePrompt(
                question.conceptTag,
                session.currentMastery(question.conceptTag),
                feedbackResult.structuredOutput(AssessmentFeedback.class),
                session.consecutiveFailures(question.conceptTag),
                session.sessionCount()
            )
        );

        // Check for escalation trigger
        if (assessment.shouldEscalate(
                session.consecutiveFailures(question.conceptTag),
                session.distressDetected())) {
            notifyEscalation(session, question.conceptTag, session.distressDetected());
        }

        return PipelineResult.feedback(
            feedbackResult.structuredOutput(AssessmentFeedback.class)
        );
    }

    /**
     * Generates a quiz on demand.
     * Called by SessionController on /session/{id}/quiz endpoint.
     */
    public Quiz generateQuiz(SessionState session, String topic,
                              int questionCount, String difficulty) {
        AgentResponse result = ctx.submitTo(AgentRole.EXECUTOR,
            quiz.quizPrompt(
                session.profile().raw(), topic, questionCount,
                difficulty, session.memoryContext()
            )
        );
        return result.structuredOutput(Quiz.class);
    }

    /**
     * Generates session todos.
     * Called by SessionController on /session/{id}/todos endpoint.
     */
    public TodoList generateTodos(SessionState session, SessionSummary summary) {
        AgentResponse result = ctx.submitTo(AgentRole.EXECUTOR,
            todo.generatePrompt(
                summary,
                session.profile().goal(),
                session.profile().sessionsPerWeek(),
                session.todoCompletionRate()
            )
        );
        return result.structuredOutput(TodoList.class);
    }

    // ── Private helpers ──────────────────────────────────────────────

    private PipelineResult runDiagnosticFlow(SessionState session, String message) {
        AgentResponse result = ctx.submitTo(AgentRole.ANALYST,
            diagnostic.diagnosticPrompt(
                session.profile().name(), session.profile().level(),
                session.profile().subject(), session.profile().topic(),
                session.profile().goal(), session.profile().profession()
            )
        );
        LearnerProfile updatedProfile = result.structuredOutput(LearnerProfile.class);
        session.updateProfile(updatedProfile);
        String intro = String.format(
            "Great — I've got a read on where you are, %s. " +
            "Let's start with %s and build from there.",
            nullSafe(updatedProfile != null ? updatedProfile.name  : null, "there"),
            nullSafe(updatedProfile != null ? updatedProfile.topic : null, "the basics")
        );
        return PipelineResult.diagnostic(updatedProfile, intro);
    }

    private PipelineResult runPlanningFlow(SessionState session, String message) {
        AgentResponse result = ctx.submitTo(AgentRole.STRATEGIST,
            planner.planningPrompt(
                session.profile().raw(),
                planner.fetchSyllabus(session.profile().subject(), session.profile().level())
            )
        );
        StudyPlan plan = result.structuredOutput(StudyPlan.class);
        session.updatePlan(plan);
        int chapterCount = 0;
        if (plan != null && plan.chapters != null && !plan.chapters.isBlank()) {
            // Structured LLM output sometimes delivers chapters joined by
            // literal "\n" (escaped) rather than a real newline. Split on
            // either so the count is correct in both cases.
            String normalised = plan.chapters.replace("\\n", "\n");
            chapterCount = (int) normalised.lines()
                .filter(s -> !s.isBlank())
                .count();
            if (chapterCount == 0) chapterCount = 1;
        }
        String intro = String.format(
            "I've drafted a study plan for **%s** — %d %s, roughly %d hour%s total. " +
            "We'll start with chapter 1. Ready when you are?",
            nullSafe(plan != null ? plan.topic : null, session.profile().topic()),
            chapterCount,
            chapterCount == 1 ? "chapter" : "chapters",
            plan != null ? plan.estimatedHours : 0,
            (plan != null && plan.estimatedHours == 1) ? "" : "s"
        );
        return PipelineResult.plan(plan, intro);
    }

    private String buildContentPrompt(SessionState session, String message) {
        return String.format(
            "Fetch grounded content for concept: '%s' | Level: %s | Question: %s",
            session.currentConcept(), session.profile().level(), message
        );
    }

    private String runTeachingDebate(SessionState session, String message, String content) {
        // Run 2-round debate: SocraticTutor vs DirectTutor — use the explicit form
        // since TutoringPipeline itself does not carry an @Debate annotation.
        try {
            DebateResult debate = debateEngine.run(
                "Which teaching style is best for this learner on this concept? " +
                "Profile: " + session.profile() + " | Concept: " + session.currentConcept(),
                new String[] { "SocraticTutorAgent", "DirectTutorAgent" },
                2,                       // maxRounds
                VoteRule.MAJORITY,
                TieBreaker.APPROVE,
                0.85f                    // convergence threshold
            );
            return debate.consensus().contains("DIRECT") ? "DIRECT" : "SOCRATIC";
        } catch (Exception e) {
            // Fallback: use profile preference if debate fails
            return "DIRECT".equals(session.profile().preferredTeachingStyle())
                   ? "DIRECT" : "SOCRATIC";
        }
    }

    private AgentResponse runSelectedTutor(String style, SessionState session,
                                            String message, String content) {
        if ("SOCRATIC".equals(style)) {
            // SocraticTutorAgent is registered under EDITOR (see its @Agent).
            // Using CRITIC here would dispatch to AssessmentAgent and fail
            // parsing the teaching output as AssessmentFeedback.
            return ctx.submitTo(AgentRole.EDITOR,
                socratic.teachingPrompt(
                    message, session.currentConcept(),
                    session.profile().level(), session.profile().bloomsLevel(),
                    session.currentMasteryPct(), content, session.memoryContext()
                )
            );
        }
        return ctx.submitTo(AgentRole.WRITER,
            direct.teachingPrompt(
                message, session.currentConcept(),
                session.profile().level(), session.profile().goal(),
                session.profile().bloomsLevel(), session.profile().learningStyle(),
                session.profile().analogyDomain(), session.profile().profession(),
                content, session.memoryContext()
            )
        );
    }

    private void notifyEscalation(SessionState session, String trigger, boolean distress) {
        ctx.submitTo(AgentRole.SUPPORT,
            escalation.teacherBriefPrompt(
                session.profile().name(), session.profile().level(),
                session.currentConcept(), session.recentHistory(),
                trigger, distress
            )
        );
    }

    /** Returns {@code value} when non-null and non-empty, otherwise {@code fallback}. */
    private static String nullSafe(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }

    /**
     * Shared Guardian entry point used by both the agentic and direct paths.
     *
     * Two-stage classification:
     *   1. Deterministic heuristic ({@link GuardianAgent#quickClassify}) —
     *      returns SAFE / DISHONESTY / DISTRESS / INJECTION for obvious cases.
     *   2. LLM reviewer — only called when the heuristic is UNCERTAIN.
     *
     * This keeps the common case (innocent learning questions) off the LLM
     * entirely, which (a) makes the guardian near-instant and (b) eliminates
     * the main source of false-positive blocks we saw with llama3.2 flagging
     * technical academic terms as INJECTION / INAPPROPRIATE.
     *
     * Emits the matching stageDone event so the UI timeline updates either way.
     */
    private String guardianVerdict(String sid, String message, String level, String modeLabel) {
        String heuristic = guardian.quickClassify(message);
        if (!"UNCERTAIN".equals(heuristic)) {
            events.stageDone(sid, "guardian", heuristic + " (fast-path)");
            log.log(Level.DEBUG, "Guardian ({0}) heuristic verdict for {1}: {2}",
                modeLabel, sid, heuristic);
            return heuristic;
        }

        // Heuristic inconclusive — consult the LLM, with null/noise protection.
        AgentResponse guardianResult = ctx.submitTo(AgentRole.SUPPORT,
            guardian.reviewPrompt(message, level));
        String rawVerdict = nullSafe(guardianResult != null ? guardianResult.content() : null, "SAFE");
        String verdict = classifyGuardianVerdict(rawVerdict);
        events.stageDone(sid, "guardian", verdict);
        log.log(Level.DEBUG, "Guardian ({0}) LLM verdict for {1}: raw=\"{2}\" → {3}",
            modeLabel, sid, rawVerdict, verdict);
        if ("INJECTION".equals(verdict) || "INAPPROPRIATE".equals(verdict)) {
            log.log(Level.INFO, "Guardian ({0}) BLOCKED in {1} — verdict={2}, rawLlmOutput={3}",
                modeLabel, sid, verdict, rawVerdict);
        }
        return verdict;
    }

    /**
     * Robustly classifies a Guardian LLM response into one of
     * {SAFE, DISTRESS, DISHONESTY, INJECTION, INAPPROPRIATE}.
     *
     * Small models (llama3.2, phi3) frequently respond with noise around the
     * verdict token — "Verdict: SAFE", "I'd say SAFE | ...", "The student …
     * SAFE". We scan for the first recognised token anywhere in the first
     * line and default to SAFE when nothing matches. Biasing toward SAFE is
     * correct for a learning product: false-blocking a real question is much
     * worse than letting a borderline one through (the output check runs
     * downstream anyway).
     */
    private static String classifyGuardianVerdict(String raw) {
        if (raw == null) return "SAFE";
        String firstLine = raw.strip().split("\\R", 2)[0].toUpperCase();
        // Longest tokens first so "INAPPROPRIATE" wins over any shorter substring overlap
        String[] tokens = { "INAPPROPRIATE", "DISHONESTY", "DISTRESS", "INJECTION", "SAFE" };
        for (String t : tokens) {
            if (firstLine.contains(t)) return t;
        }
        return "SAFE";
    }
}
