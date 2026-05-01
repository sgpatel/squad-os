package io.tutoros.pipeline;

import io.squados.annotation.*;
import io.squados.context.SquadContext;
import io.squados.agent.AgentResponse;
import io.squados.debate.DebateEngine;
import io.squados.debate.DebateResult;
import io.squados.memory.MemoryManager;
import io.squados.memory.annotation.Importance;
import io.tutoros.agent.*;
import io.tutoros.memory.TurnContext;
import io.tutoros.memory.TutorOsMemoryManager;
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
    private final IntentAnalyzerAgent intentAnalyzer;
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

    /**
     * MemoryManager — nullable. When wired (see tutor-api MemoryConfig),
     * the pipeline writes a salient end-of-turn record to EPISODIC memory
     * so future turns retrieve it via every agent's {@code @AgentMemory}.
     * Null in tests / CLI contexts; the write call short-circuits cleanly.
     *
     * Phase 3 (framework PR): replace this explicit write with AOP-driven
     * automatic writes via method-level {@code @Memory} annotations.
     */
    private final MemoryManager memoryManager;

    public TutoringPipeline(SquadContext ctx,
                             GuardianAgent guardian,
                             DiagnosticAgent diagnostic,
                             CurriculumPlannerAgent planner,
                             ContentAgent content,
                             IntentAnalyzerAgent intentAnalyzer,
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
        this(ctx, guardian, diagnostic, planner, content, intentAnalyzer, socratic, direct,
             practice, assessment, progress, escalation, todo, quiz,
             visualisation, debateEngine, PipelineEventBus.NOOP, null);
    }

    /**
     * Constructor with explicit event bus. Memory is disabled (null manager).
     * Kept for tests / CLI that don't run a WS or memory subsystem.
     */
    public TutoringPipeline(SquadContext ctx,
                             GuardianAgent guardian,
                             DiagnosticAgent diagnostic,
                             CurriculumPlannerAgent planner,
                             ContentAgent content,
                             IntentAnalyzerAgent intentAnalyzer,
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
        this(ctx, guardian, diagnostic, planner, content, intentAnalyzer, socratic, direct,
             practice, assessment, progress, escalation, todo, quiz,
             visualisation, debateEngine, events, null);
    }

    /**
     * Full constructor — production path. Adds {@code memoryManager} so
     * end-of-turn records persist to EPISODIC memory and are recalled by
     * every {@code @AgentMemory}-annotated agent on subsequent turns.
     * Pass {@code null} to disable memory writes (read still works
     * independently via {@link SquadContext#setMemoryManager}).
     */
    public TutoringPipeline(SquadContext ctx,
                             GuardianAgent guardian,
                             DiagnosticAgent diagnostic,
                             CurriculumPlannerAgent planner,
                             ContentAgent content,
                             IntentAnalyzerAgent intentAnalyzer,
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
                             PipelineEventBus events,
                             MemoryManager memoryManager) {
        this.ctx           = ctx;
        this.guardian      = guardian;
        this.diagnostic    = diagnostic;
        this.planner       = planner;
        this.content       = content;
        this.intentAnalyzer = intentAnalyzer;
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
        this.memoryManager = memoryManager;
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
        // Bind the per-turn context BEFORE any agent runs so that
        // @AgentMemory reads in AgentWrapper see a non-null squadId and
        // hard-filter recall by the active subject.
        TutorOsMemoryManager.CTX.set(buildTurnContext(session));
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

            // ── Step 1.5: Subject/Topic disambiguation gate ──────────────
            // Refuse to guess across subjects. If the message can't be
            // confidently placed inside the active session subject (or the
            // session has no subject yet), ask the learner to confirm
            // BEFORE running ContentAgent / debate / tutor — those steps
            // happily produce mixed-subject content otherwise.
            //
            // First session is exempt: DiagnosticAgent below is responsible
            // for setting the subject in the first place.
            if (!session.isFirstSession()) {
                PipelineResult disambiguation =
                    maybeDisambiguateSubject(sid, session, message);
                if (disambiguation != null) {
                    events.done(sid);
                    return disambiguation;
                }
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

            // ── Step 3.5: Visualisation ──────────────────────────────────
            // Two triggers, either is sufficient:
            //   (a) ContentAgent fired the requestVisualisation tool — its
            //       sentinel JSON {"…","status":"QUEUED"} lands in the
            //       grounded text.
            //   (b) The user message itself reads like a visualise request
            //       ("show me the structure of …", "draw …", "plot …",
            //        "diagram", "visualise/visualize", "graph of …", etc).
            //
            // (b) exists because LLMs frequently pick wikipedia/wolfram over
            // requestVisualisation even when the learner explicitly asks for
            // a diagram. Triggering deterministically on the user's intent
            // makes the diagram path reliable instead of LLM-mood-dependent.
            VisualAsset asset = maybeRunVisualisation(sid, message, groundedContent, session);

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
            // even on backends that don't stream tokens. When a visual asset
            // was produced for this turn, include it in the payload so clients
            // that missed the dedicated VISUAL frame (e.g. polling, replays)
            // still see the diagram on the same message.
            java.util.Map<String, Object> messagePayload = new java.util.LinkedHashMap<>();
            messagePayload.put("body",          tutorResponse);
            messagePayload.put("teachingStyle", winnerStyle);
            if (asset != null) messagePayload.put("visualAsset", asset);
            events.message(sid, messagePayload);

            // Persist a salient end-of-turn record so future turns recall
            // what the learner asked, which style won the debate, and the
            // shape of the answer. Read path is automatic via @AgentMemory
            // on every tutor agent. Best-effort — never block the response.
            writeTurnMemory(session, message, tutorResponse, winnerStyle);

            events.done(sid);

            return PipelineResult.tutor(tutorResponse, winnerStyle, groundedContent);
        } catch (RuntimeException ex) {
            events.stageError(sid, "tutor", ex.getMessage() != null ? ex.getMessage() : ex.toString());
            events.done(sid);
            throw ex;
        } finally {
            // Clear the per-turn context unconditionally — leaving it set
            // would leak the active subject filter into the next request
            // on the same thread.
            TutorOsMemoryManager.CTX.remove();
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
        ctx.submitTo(AgentRole.HEALER,
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
        AgentResponse result = ctx.submitTo(AgentRole.DPS,
            quiz.quizPrompt(
                session.profile().raw(), topic, questionCount,
                difficulty, session.memoryContext()
            )
        );
        Quiz parsed = result.structuredOutput(Quiz.class);
        if (parsed == null) {
            // Structured-output parse miss — surface the raw agent reply
            // (or class name if even that's empty) so the controller can
            // include it in the 502 body and the operator can see what
            // the LLM actually said. Throwing here is preferable to
            // returning null because the controller has dedicated
            // exception handling that includes root-cause messages.
            String raw = result.content();
            String snippet = (raw == null || raw.isBlank())
                ? "(empty response from agent)"
                : raw.length() > 400 ? raw.substring(0, 400) + "…" : raw;
            throw new IllegalStateException(
                "Quiz agent returned no parseable Quiz JSON. Raw reply: " + snippet
            );
        }
        return parsed;
    }

    /**
     * Generates session todos.
     * Called by SessionController on /session/{id}/todos endpoint.
     */
    public TodoList generateTodos(SessionState session, SessionSummary summary) {
        AgentResponse result = ctx.submitTo(AgentRole.WILDCARD,
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
        if (plan == null) {
            // Planner LLM call failed or returned unparseable JSON.
            // Don't crash the pipeline — surface a graceful message so the
            // UI can tell the learner to retry. The raw text (if any) may
            // contain the agent's apology / partial reasoning.
            String raw = result != null ? result.content() : null;
            String fallback = (raw != null && !raw.isBlank())
                ? raw
                : "I couldn't draft a study plan this time — the planning agent didn't return a usable response. "
                + "Please try again in a moment.";
            return PipelineResult.blocked(fallback);
        }
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
        // Pin subject + topic into the ContentAgent prompt so the LLM's
        // tool-call decisions (Khan / Wikipedia / Wolfram / analogy /
        // visualisation) stay inside the active subject. Without this,
        // a maths question can pull a chemistry analogy or a biology
        // wikipedia article when the concept name happens to be ambiguous
        // (e.g. "function", "cell", "compound").
        String subj  = session.profile().subject();
        String topc  = session.profile().topic();
        String scope = (subj != null && !subj.isBlank())
            ? String.format(" | Subject: %s%s", subj,
                (topc != null && !topc.isBlank()) ? " | Topic: " + topc : "")
            : "";
        return String.format(
            "Fetch grounded content for concept: '%s' | Level: %s%s | Question: %s%n" +
            "Stay strictly within the stated subject — if no relevant source " +
            "exists inside the subject, return nothing rather than reaching " +
            "into a different subject.",
            session.currentConcept(), session.profile().level(), scope, message
        );
    }

    private String runTeachingDebate(SessionState session, String message, String content) {
        // Prefer a single-shot intent analysis over the 2-round debate. It's
        // ~10× faster (one LLM call vs. six) and — with OpenAI — already
        // accurate enough. The debate remains as a fallback.
        try {
            String conversationHistory = nullSafe(session.recentHistory(), "(no prior turns)");
            String learnerProfile = String.format(
                "Level: %s | Style: %s | Mastery: %d%% | Goal: %s",
                nullSafe(session.profile().level(),         "higher-sec"),
                nullSafe(session.profile().learningStyle(), "DIRECT"),
                session.currentMasteryPct(),
                nullSafe(session.profile().goal(),          "DEEP_UNDERSTANDING")
            );

            // Route to SCOUT — IntentAnalyzerAgent's role. Using ANALYST would
            // collide with DiagnosticAgent and dispatch the wrong agent.
            AgentResponse intentResult = ctx.submitTo(AgentRole.SCOUT,
                intentAnalyzer.analyzeIntent(message, conversationHistory, learnerProfile)
            );

            // Parse the structured output rather than substring-matching the
            // raw content (the `reasoning` field frequently contains the word
            // "DIRECT" even when the recommendation is SOCRATIC).
            IntentAnalysis analysis = intentResult.structuredOutput(IntentAnalysis.class);
            if (analysis != null && analysis.recommendedStyle != null) {
                String style = analysis.recommendedStyle.trim().toUpperCase();
                if ("SOCRATIC".equals(style) || "DIRECT".equals(style)) {
                    log.log(Level.INFO,
                        "Intent → style={0} confidence={1} reason={2}",
                        style, analysis.confidence, nullSafe(analysis.reasoning, ""));
                    return style;
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Intent analysis failed, falling back to debate: {0}",
                    e.getMessage() != null ? e.getMessage() : e.toString());
        }

        // Fallback: Run 2-round debate if intent analysis fails
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
            // Final fallback: default to DIRECT (better to over-explain than frustrate)
            return "DIRECT";
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
        ctx.submitTo(AgentRole.TANK,
            escalation.teacherBriefPrompt(
                session.profile().name(), session.profile().level(),
                session.currentConcept(), session.recentHistory(),
                trigger, distress
            )
        );
    }

    /**
     * Detects whether ContentAgent fired the {@code requestVisualisation}
     * tool during step 3 and, if so, runs VisualisationAgent inline.
     *
     * The tool returns a sentinel JSON of shape
     * {@code {"type":"<chem|plot>","concept":"<topic>","level":"<level>","status":"QUEUED"}}.
     * That sentinel is part of the LLM's grounded output so we scan for it
     * with a tolerant regex (the LLM may quote / re-format whitespace).
     *
     * Side effects on success:
     *   - emits a {@code VISUAL} frame via {@link PipelineEventBus#visual}
     *     so the UI can render the diagram before / alongside the tutor reply.
     *
     * Returns the parsed {@link VisualAsset} on success, {@code null} otherwise.
     * Failures are swallowed (logged at WARNING) — visualisation is best-effort
     * and must never block the teaching turn.
     */
    private VisualAsset maybeRunVisualisation(String sid, String userMessage,
                                               String groundedContent,
                                               SessionState session) {
        // Trigger A: ContentAgent fired requestVisualisation — sentinel in grounded text.
        boolean toolFired = groundedContent != null && (
            groundedContent.contains("\"status\":\"QUEUED\"") ||
            groundedContent.contains("\"status\": \"QUEUED\"")
        );
        // Trigger B: deterministic detection on the user's own message.
        boolean userAskedForVisual = looksLikeVisualisationRequest(userMessage);

        if (!toolFired && !userAskedForVisual) return null;

        try {
            String concept = null;
            String typeHint = null;
            if (toolFired) {
                concept  = extractJsonField(groundedContent, "concept");
                typeHint = extractJsonField(groundedContent, "type");
            }
            if (concept == null || concept.isBlank()) {
                concept = sniffConceptFromMessage(userMessage);
            }
            if (concept == null || concept.isBlank()) {
                concept = nullSafe(session.currentConcept(), "the current topic");
            }
            String level = nullSafe(session.profile().level(), "higher-sec");

            // Hard-route the type before the LLM ever sees the prompt.
            // The tool's typeHint wins when present (the LLM had context on
            // the user's intent); otherwise the deterministic keyword router
            // decides. This prevents weak models from drifting into "chem"
            // and emitting a default caffeine SMILES for, e.g., "sin theta".
            String pinnedType = (typeHint != null && !typeHint.isBlank())
                ? typeHint.trim().toLowerCase()
                : visualisation.selectRenderType(concept);

            log.log(Level.INFO,
                "Visualisation triggered for sid={0} concept={1} pinnedType={2} via={3}",
                sid, concept, pinnedType, toolFired ? "tool" : "user-intent");

            AgentResponse vizResult = ctx.submitTo(AgentRole.VISIONARY,
                visualisation.visualPrompt(concept, level, pinnedType));
            VisualAsset asset = vizResult != null
                ? vizResult.structuredOutput(VisualAsset.class)
                : null;
            if (asset == null || asset.specJson == null || asset.specJson.isBlank()) {
                log.log(Level.WARNING,
                    "VisualisationAgent returned no usable VisualAsset for sid={0}", sid);
                return null;
            }
            events.visual(sid, asset);
            return asset;
        } catch (RuntimeException ex) {
            log.log(Level.WARNING, "Visualisation pipeline step failed for sid={0}: {1}",
                sid, ex.getMessage() != null ? ex.getMessage() : ex.toString());
            return null;
        }
    }

    /**
     * Cheap keyword classifier for "the user is explicitly asking for a
     * diagram / plot / structure". Errs on the side of false-negatives —
     * we'd rather miss a borderline case than render a diagram nobody asked
     * for. Matches phrases the FE's own "Visualize" follow-up button emits
     * ("Produce a clear labelled SVG diagram …") plus common natural-language
     * variants.
     */
    private static boolean looksLikeVisualisationRequest(String message) {
        if (message == null) return false;
        String m = message.toLowerCase();
        // Strong, unambiguous markers.
        String[] strong = {
            "visualise", "visualize", "labelled svg", "labeled svg",
            "draw a diagram", "draw the diagram", "draw a structure",
            "draw the structure", "show me the structure", "show structure of",
            "structure of ", "molecular structure", "skeletal structure",
            "diagram of ", "diagram for ", "svg diagram",
            "plot of ", "plot the ", "graph of ", "graph the ",
            "chart of ", "chart the "
        };
        for (String s : strong) {
            if (m.contains(s)) return true;
        }
        // Verb + diagram/plot/graph/chart anywhere — softer match.
        boolean hasVerb = m.contains("show ") || m.contains("draw ") ||
                          m.contains("render ") || m.contains("sketch ") ||
                          m.contains("illustrate ");
        boolean hasNoun = m.contains("diagram") || m.contains("plot") ||
                          m.contains("graph") || m.contains("chart") ||
                          m.contains("structure");
        return hasVerb && hasNoun;
    }

    /**
     * Best-effort concept extraction from a user's visualise request. Strips
     * the imperative wrapper ("show me the structure of …") so the
     * VisualisationAgent prompt receives the bare topic. Falls back to the
     * full message when no obvious wrapper is found — the agent's own
     * structured-output prompt is robust to extra prose.
     */
    private static String sniffConceptFromMessage(String message) {
        if (message == null || message.isBlank()) return null;
        String m = message.trim();
        String lower = m.toLowerCase();
        String[] prefixes = {
            "show me the structure of ", "show me the structure for ",
            "show me a diagram of ", "show me the diagram of ",
            "show the structure of ", "show structure of ",
            "draw the structure of ", "draw a structure of ",
            "draw a diagram of ",      "draw the diagram of ",
            "diagram of ",             "diagram for ",
            "plot of ",                "plot the ",
            "graph of ",               "graph the ",
            "structure of ",
            "visualise ", "visualize ",
            "render ", "sketch ", "illustrate "
        };
        for (String pfx : prefixes) {
            int i = lower.indexOf(pfx);
            if (i >= 0) {
                String tail = m.substring(i + pfx.length()).trim();
                // Drop anything after the first sentence terminator.
                int cut = -1;
                for (char c : new char[] { '.', '?', '!', '\n' }) {
                    int k = tail.indexOf(c);
                    if (k >= 0 && (cut < 0 || k < cut)) cut = k;
                }
                if (cut > 0) tail = tail.substring(0, cut).trim();
                if (!tail.isBlank()) return tail;
            }
        }
        // No prefix matched — use the message as-is, trimmed to keep prompts tight.
        return m.length() > 160 ? m.substring(0, 160) : m;
    }

    /**
     * Extracts a string field from the FIRST JSON-like object in {@code text}
     * matching the QUEUED visualisation sentinel. Tolerates surrounding prose
     * and whitespace variations the LLM may introduce when echoing the tool
     * result back into its content stream.
     */
    private static String extractJsonField(String text, String field) {
        // Match: "field"  :  "value"  — non-greedy on value, allow escaped quotes.
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        );
        java.util.regex.Matcher m = p.matcher(text);
        if (!m.find()) return null;
        return m.group(1)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    /**
     * Persist a single end-of-turn record to EPISODIC memory.
     *
     * Content shape: a compact, retrieval-friendly summary of THIS turn —
     * what the learner asked, which teaching style won, and the first
     * 200 chars of the response. The next turn's @AgentMemory read on
     * any tutor agent will pull this back via cosine similarity against
     * the new query, so e.g. a learner who asked about "cell membranes"
     * yesterday will find "[turn 12-Mar] You asked about cell membranes
     * — DirectTutor explained …" injected into the system prompt today.
     *
     * Best-effort:
     *   - silent no-op when memoryManager is null (tests, CLI, missing config)
     *   - all exceptions swallowed and logged at DEBUG so a flaky embedder
     *     or DB outage cannot break the user-visible response
     *
     * Squad id: derived from the squad name registered with SquadContext.
     * Agent id: null (SQUAD scope — any agent in the squad can recall it).
     */
    private void writeTurnMemory(SessionState session, String userMessage,
                                 String tutorResponse, String winnerStyle) {
        if (memoryManager == null) return;
        try {
            String squadId = (ctx != null && ctx.getConfig() != null
                              && ctx.getConfig().getName() != null)
                ? ctx.getConfig().getName()
                : "tutor-os";
            String sessionId = session.sessionId();

            // Compact, embedding-friendly. Stays well under typical token budgets.
            String snippet = tutorResponse == null ? ""
                : tutorResponse.length() <= 240 ? tutorResponse
                : tutorResponse.substring(0, 240) + "…";
            String content = String.format(
                "Learner asked: %s%nTutor (%s style) replied: %s",
                userMessage, winnerStyle, snippet);

            // Tags are how TutorOsMemoryManager.read filters records by
            // active subject/topic. Lower-cased for stable matching against
            // the ThreadLocal TurnContext on the read side.
            String subj = session.profile().subject();
            String topc = session.profile().topic();
            java.util.List<String> tagList = new java.util.ArrayList<>();
            tagList.add("session:" + sessionId);
            tagList.add("style:"   + (winnerStyle != null ? winnerStyle.toLowerCase() : "unknown"));
            tagList.add("level:"   + session.profile().level());
            if (subj != null && !subj.isBlank()) tagList.add("subject:" + subj.trim().toLowerCase());
            if (topc != null && !topc.isBlank()) tagList.add("topic:"   + topc.trim().toLowerCase());
            String[] tags = tagList.toArray(new String[0]);

            memoryManager.write(
                MemoryHelper.episodicSquadWrite(Importance.MEDIUM, tags),
                /* agentId  */ null,        // SQUAD scope
                /* squadId  */ squadId,
                /* sessionId*/ sessionId,
                /* content  */ content
            );
        } catch (RuntimeException e) {
            log.log(Level.DEBUG, "writeTurnMemory failed (non-fatal): {0}", e.getMessage());
        }
    }

    /**
     * Build the per-turn {@link TurnContext} from session state. Lives here
     * so the memory layer doesn't have to know about SessionState shape.
     *
     * Read by {@link TutorOsMemoryManager} via its {@code ThreadLocal} —
     * see the class comment there for why the indirection exists.
     */
    private TurnContext buildTurnContext(SessionState session) {
        String squadId = (ctx != null && ctx.getConfig() != null
                          && ctx.getConfig().getName() != null)
            ? ctx.getConfig().getName()
            : "tutor-os";
        String subject = session != null && session.profile() != null
            ? session.profile().subject() : null;
        String topic   = session != null && session.profile() != null
            ? session.profile().topic()   : null;
        String sid     = session != null ? session.sessionId() : null;
        return TurnContext.of(squadId, subject, topic, sid);
    }

    /**
     * Subject/topic disambiguation gate.
     *
     * Returns {@code null} when the message is safe to route through the
     * normal pipeline; returns a {@link PipelineResult#safe} response that
     * asks the learner for clarification when the message either:
     *
     *   (a) targets a different subject from the active session, or
     *   (b) carries no clear subject signal AND the session has no active
     *       subject locked in.
     *
     * Implementation strategy:
     *   1. Cheap heuristic first — if the active subject keyword appears
     *      in the message, accept. Avoids an LLM call on every turn for
     *      the common case (learner stays on topic).
     *   2. Otherwise: a single short LLM classification via
     *      {@link IntentAnalyzerAgent#subjectMatchPrompt}, returning one
     *      of MATCHES / DIFFERENT / UNKNOWN.
     *   3. On DIFFERENT or UNKNOWN: emit a clarification MESSAGE so the
     *      UI shows it on the chat thread, then short-circuit.
     */
    private PipelineResult maybeDisambiguateSubject(String sid, SessionState session, String message) {
        String activeSubject = session.profile().subject();
        String activeTopic   = session.profile().topic();

        // No active subject yet — ask before producing any content.
        if (activeSubject == null || activeSubject.isBlank()) {
            return askForSubjectClarification(sid, session, message,
                "Which subject would you like to study right now? " +
                "Reply with the subject (and ideally the topic) so I can " +
                "stay focused and not mix material from elsewhere.");
        }

        // Cheap heuristic: subject keyword present → accept.
        String low = message == null ? "" : message.toLowerCase();
        if (low.contains(activeSubject.trim().toLowerCase())) {
            return null;
        }

        // LLM classification, single small call. Failures fall through to
        // the safe default (assume MATCHES) so a flaky model never blocks
        // the learner unnecessarily.
        //
        // Uses GuardianAgent (SUPPORT role) because:
        //   1. It already serves as the input-gate agent — disambiguation
        //      is a natural extension of input gating.
        //   2. It has no @StructuredOutput, so the response stays plain text
        //      (one of MATCHES / DIFFERENT / UNKNOWN) and we parse it
        //      ourselves — no schema collision with IntentAnalysis or
        //      StudyPlan.
        String verdict;
        try {
            AgentResponse cls = ctx.submitTo(AgentRole.SUPPORT,
                guardian.subjectMatchPrompt(message, activeSubject, activeTopic));
            verdict = GuardianAgent.classifySubjectMatch(
                cls != null ? cls.content() : null);
        } catch (RuntimeException e) {
            log.log(Level.DEBUG, "subject-match classifier failed: {0}", e.getMessage());
            return null;
        }

        if ("MATCHES".equals(verdict)) return null;

        if ("DIFFERENT".equals(verdict)) {
            String body = String.format(
                "Just to keep things focused — your current subject is **%s**" +
                "%s, but this question looks like it's about something else.%n%n" +
                "Would you like me to:%n" +
                "1. Answer it inside **%s** (if it fits), or%n" +
                "2. Switch the session to the new subject (tell me which one)?%n%n" +
                "I'd rather check than mix subjects.",
                activeSubject,
                activeTopic != null && !activeTopic.isBlank()
                    ? " (topic: **" + activeTopic + "**)" : "",
                activeSubject);
            return askForSubjectClarification(sid, session, message, body);
        }

        // UNKNOWN — message subject is genuinely unclear.
        return askForSubjectClarification(sid, session, message,
            "I'm not sure which subject this question belongs to. " +
            "Could you tell me — is it part of **" + activeSubject + "**, " +
            "or a different subject?");
    }

    /** Emit a clarification MESSAGE frame and return a safe PipelineResult. */
    private PipelineResult askForSubjectClarification(
            String sid, SessionState session, String userMessage, String body) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("body", body);
        payload.put("teachingStyle", "CLARIFY");
        payload.put("clarification", "subject");
        events.message(sid, payload);
        return PipelineResult.safe(body);
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
