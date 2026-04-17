package io.tutoros.pipeline;

import io.tutoros.model.*;

/**
 * Sealed result type returned by TutoringPipeline for every operation.
 *
 * The API layer pattern-matches on the subtype to determine the HTTP
 * response shape sent back to the UI.
 *
 * Types:
 *   TutorResponse   — normal tutor reply (text + metadata)
 *   DiagnosticResult— initial diagnostic complete, profile built
 *   PlanResult      — new study plan generated
 *   FeedbackResult  — assessment feedback after practice answer
 *   SafeRefusal     — blocked by GuardianAgent (distress / dishonesty)
 *   BlockedResult   — hard block (injection / inappropriate content)
 *   EscalationResult— session paused, teacher notified
 */
public sealed interface PipelineResult
    permits PipelineResult.TutorResponse,
            PipelineResult.DiagnosticResult,
            PipelineResult.PlanResult,
            PipelineResult.FeedbackResult,
            PipelineResult.SafeRefusal,
            PipelineResult.BlockedResult,
            PipelineResult.EscalationResult {

    // ── Factory methods ───────────────────────────────────────────────

    static PipelineResult tutor(String text, String teachingStyle, String sourceContext) {
        return new TutorResponse(text, teachingStyle, sourceContext, false);
    }

    static PipelineResult safe(String text) {
        return new SafeRefusal(text);
    }

    static PipelineResult blocked(String reason) {
        return new BlockedResult(reason);
    }

    static PipelineResult diagnostic(LearnerProfile profile, String introMessage) {
        return new DiagnosticResult(profile, introMessage);
    }

    static PipelineResult plan(StudyPlan plan, String introMessage) {
        return new PlanResult(plan, introMessage);
    }

    static PipelineResult feedback(AssessmentFeedback feedback) {
        return new FeedbackResult(feedback);
    }

    static PipelineResult escalation(String teacherBrief, String studentMessage) {
        return new EscalationResult(teacherBrief, studentMessage);
    }

    // ── Subtypes ──────────────────────────────────────────────────────

    /**
     * Normal tutor reply — the most common result type.
     * text          = the tutor's streamed response
     * teachingStyle = DIRECT | SOCRATIC (winner of @Debate)
     * sourceContext = grounded content snippet used (for transparency)
     * visualQueued  = true when VisualisationAgent has a render in progress
     */
    record TutorResponse(
        String  text,
        String  teachingStyle,
        String  sourceContext,
        boolean visualQueued
    ) implements PipelineResult {}

    /**
     * Returned after the initial diagnostic completes.
     * The API sends back both the LearnerProfile (for UI to render profile card)
     * and an introductory message from the tutor.
     */
    record DiagnosticResult(
        LearnerProfile profile,
        String         introMessage
    ) implements PipelineResult {}

    /**
     * Returned when CurriculumPlannerAgent generates a new StudyPlan.
     * The API sends the plan to the UI for the chapters screen.
     */
    record PlanResult(
        StudyPlan plan,
        String    introMessage
    ) implements PipelineResult {}

    /**
     * Returned after AssessmentAgent grades a practice answer.
     */
    record FeedbackResult(
        AssessmentFeedback feedback
    ) implements PipelineResult {}

    /**
     * Returned by GuardianAgent for distress or academic dishonesty.
     * text = the safe, compassionate refusal message shown to the student.
     */
    record SafeRefusal(
        String text
    ) implements PipelineResult {}

    /**
     * Returned for hard blocks (injection, inappropriate).
     * Not shown to the student in detail — generic error message displayed.
     */
    record BlockedResult(
        String reason
    ) implements PipelineResult {}

    /**
     * Returned when EscalationAgent fires.
     * Session is paused; teacherBrief is sent to the teacher portal.
     * studentMessage = what to show the student while they wait.
     */
    record EscalationResult(
        String teacherBrief,
        String studentMessage
    ) implements PipelineResult {}
}
