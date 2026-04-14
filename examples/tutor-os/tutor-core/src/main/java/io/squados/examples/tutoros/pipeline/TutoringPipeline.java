package io.squados.examples.tutoros.pipeline;

import io.squados.annotation.*;
import io.squados.context.SquadContext;
import io.squados.context.AgentResponse;
import io.squados.debate.DebateEngine;
import io.squados.debate.DebateResult;
import io.squados.examples.tutoros.agent.*;
import io.squados.examples.tutoros.model.*;

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
    }

    /**
     * Entry point — called by SessionController for every student message.
     *
     * @param session current session state (learner profile, chapter, history)
     * @param message raw student message text
     * @return PipelineResult containing the tutor response + metadata
     */
    public PipelineResult process(SessionState session, String message) {

        // ── Step 1: Guardian input check ────────────────────────────
        AgentResponse guardianResult = ctx.submit(
            AgentRole.SUPPORT, "guardian",
            guardian.reviewPrompt(message, session.profile().level())
        );

        String verdict = guardianResult.text();
        if (verdict.startsWith("DISTRESS")) {
            notifyEscalation(session, message, true);
            return PipelineResult.safe(guardian.distressResponse(session.profile().name()));
        }
        if (verdict.startsWith("DISHONESTY")) {
            return PipelineResult.safe(guardian.dishonestyResponse());
        }
        if (verdict.startsWith("INJECTION") || verdict.startsWith("INAPPROPRIATE")) {
            return PipelineResult.blocked("Message blocked by safety filter.");
        }

        // ── Step 2: Route — diagnose / plan / continue ───────────────
        if (session.isFirstSession()) {
            return runDiagnosticFlow(session, message);
        }
        if (session.needsNewPlan()) {
            return runPlanningFlow(session, message);
        }

        // ── Step 3: Content fetch (tool calls) ───────────────────────
        AgentResponse contentResult = ctx.submit(
            AgentRole.RESEARCHER, "content",
            buildContentPrompt(session, message)
        );
        String groundedContent = contentResult.text();

        // ── Step 4: Teaching style debate ────────────────────────────
        String winnerStyle = runTeachingDebate(session, message, groundedContent);

        // ── Step 5: Tutor responds ────────────────────────────────────
        AgentResponse tutorResult = runSelectedTutor(
            winnerStyle, session, message, groundedContent
        );
        String tutorResponse = tutorResult.text();

        // ── Step 6: Guardian output check ────────────────────────────
        AgentResponse outputCheck = ctx.submit(
            AgentRole.SUPPORT, "guardian-output",
            guardian.outputCheckPrompt(tutorResponse, session.profile().level())
        );
        if (outputCheck.text().startsWith("FAIL")) {
            // Re-run direct tutor with a corrective instruction
            tutorResult = ctx.submit(AgentRole.WRITER, "direct-tutor-retry",
                tutorResponse + "\n\nFix: " + outputCheck.text());
            tutorResponse = tutorResult.text();
        }

        return PipelineResult.tutor(tutorResponse, winnerStyle, groundedContent);
    }

    /**
     * Runs a practice question cycle — generate → student answers → assess.
     * Called by SessionController when student submits a practice answer.
     */
    public PipelineResult assess(SessionState session, String studentAnswer,
                                  PracticeQuestion question, int attemptNumber) {

        AgentResponse feedbackResult = ctx.submit(
            AgentRole.CRITIC, "assessment",
            assessment.gradingPrompt(
                question, studentAnswer, attemptNumber,
                session.profile().level(), session.profile().goal(),
                session.memoryContext()
            )
        );

        // Update mastery via ProgressAgent
        ctx.submit(AgentRole.SUPPORT, "progress-update",
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
        AgentResponse result = ctx.submit(
            AgentRole.EXECUTOR, "quiz",
            quiz.quizPrompt(
                session.profile(), topic, questionCount,
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
        AgentResponse result = ctx.submit(
            AgentRole.EXECUTOR, "todo",
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
        AgentResponse result = ctx.submit(
            AgentRole.ANALYST, "diagnostic",
            diagnostic.diagnosticPrompt(
                session.profile().name(), session.profile().level(),
                session.profile().subject(), session.profile().topic(),
                session.profile().goal(), session.profile().profession()
            )
        );
        LearnerProfile updatedProfile = result.structuredOutput(LearnerProfile.class);
        session.updateProfile(updatedProfile);
        return PipelineResult.diagnostic(updatedProfile, result.text());
    }

    private PipelineResult runPlanningFlow(SessionState session, String message) {
        AgentResponse result = ctx.submit(
            AgentRole.STRATEGIST, "curriculum-planner",
            planner.planningPrompt(
                session.profile(),
                planner.fetchSyllabus(session.profile().subject(), session.profile().level())
            )
        );
        StudyPlan plan = result.structuredOutput(StudyPlan.class);
        session.updatePlan(plan);
        return PipelineResult.plan(plan, result.text());
    }

    private String buildContentPrompt(SessionState session, String message) {
        return String.format(
            "Fetch grounded content for concept: '%s' | Level: %s | Question: %s",
            session.currentConcept(), session.profile().level(), message
        );
    }

    private String runTeachingDebate(SessionState session, String message, String content) {
        // Run 2-round debate: SocraticTutor vs DirectTutor
        try {
            DebateResult debate = debateEngine.run(
                "Which teaching style is best for this learner on this concept? " +
                "Profile: " + session.profile() + " | Concept: " + session.currentConcept()
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
            return ctx.submit(AgentRole.CRITIC, "socratic-tutor",
                socratic.teachingPrompt(
                    message, session.currentConcept(),
                    session.profile().level(), session.profile().bloomsLevel(),
                    session.currentMasteryPct(), content, session.memoryContext()
                )
            );
        }
        return ctx.submit(AgentRole.WRITER, "direct-tutor",
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
        ctx.submit(AgentRole.SUPPORT, "escalation",
            escalation.teacherBriefPrompt(
                session.profile().name(), session.profile().level(),
                session.currentConcept(), session.recentHistory(),
                trigger, distress
            )
        );
    }
}
