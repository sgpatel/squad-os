package io.tutoros.agent;

import io.squados.annotation.*;
import io.tutoros.model.LearnerProfile;
import io.tutoros.model.StudyPlan;
import io.tutoros.model.Syllabus;
import io.squados.remote.SquadClient;

/**
 * Curriculum Planner Agent — builds a personalised study plan from the
 * LearnerProfile produced by DiagnosticAgent.
 *
 * Uses @AutoPlan to iterate until ALL gap concepts from the profile are
 * covered by at least one chapter in the plan. On each iteration:
 *   PLAN    — draft the chapter list, mapping gap concepts to chapters
 *   EXECUTE — estimate hours per chapter based on learner pace
 *   REFLECT — are all gaps covered? Are any chapters too large?
 *   REPLAN  — split overloaded chapters, merge thin ones, fill gaps
 *
 * Connects to the school LMS via @RemoteSquad to fetch the official
 * syllabus and ensure every mandatory topic is in the plan.
 *
 * Features:
 *   @AutoPlan      — plan-execute-reflect loop, max 4 iterations
 *   @StructuredOutput — typed StudyPlan output
 *   @RemoteSquad   — fetches official syllabus from school LMS
 *   @DurableAgent  — plan is checkpointed; resumable after restart
 *   @Traced        — full span per planning session
 */
@Agent(
    role        = AgentRole.STRATEGIST,
    name        = "CurriculumPlannerAgent",
    description = "Builds a personalised chapter-by-chapter study plan from the " +
                  "learner's gap profile and official syllabus. Iterates until all " +
                  "gaps are covered and the weekly session target is achievable."
)
// NOTE: @AutoPlan targets METHODS only — when SquadOS adds class-level autoplanning
// in a future release, the iteration goal/policy below can be reinstated.
@StructuredOutput(schema = StudyPlan.class, retryOnMalformed = true, maxRetries = 2)
@DurableAgent(store = "memory", ttlHours = 168) // plan persists for a week
@Traced(spanName = "curriculum-planning")
public class CurriculumPlannerAgent {

    /**
     * School LMS connection — fetches official syllabus for the subject/level.
     * Injected by SquadOS RemoteSquadInvoker at boot.
     *
     * Feature: @RemoteSquad
     */
    @RemoteSquad(url = "${tutor.lms.url}", auth = "api-key", timeoutMs = 10000)
    private SquadClient lmsClient;

    /**
     * Main planning prompt.
     *
     * The @AutoPlan engine will call this with the accumulated plan draft
     * on each iteration, appending the reflection output from the previous round.
     */
    public String planningPrompt(LearnerProfile profile, String officialSyllabus) {
        return """
            You are an expert curriculum designer for %s-level %s learners.

            Learner: %s
            Subject: %s | Topic: %s
            Goal: %s

            Gap concepts (MUST be covered):
            %s

            Mastered concepts (skip these or use as foundations only):
            %s

            Official syllabus requirements:
            %s

            Learner pace: %d sessions/week × %d min/session

            Design a chapter-by-chapter study plan:
            1. Each chapter should take one session (max 45 min content)
            2. Order chapters so each builds on the previous
            3. Prioritise gap concepts — they must appear before optional depth
            4. For PASS_EXAMS goal: include a "Practice & Past Papers" chapter last
            5. For DEEP_UNDERSTANDING goal: include "Extension & Real-World Applications"
            6. For QUICK_REVISION goal: limit to 3–4 essential chapters only

            Output a complete JSON StudyPlan.
            """.formatted(
                profile.level, profile.subject,
                profile.name, profile.subject, profile.topic, profile.goal,
                profile.gapConcepts,
                profile.masteredConcepts,
                officialSyllabus,
                profile.sessionsPerWeek, profile.avgSessionMinutes
            );
    }

    /**
     * Fetch official syllabus from the school LMS.
     * Falls back to a built-in default syllabus if LMS is unavailable.
     */
    public String fetchSyllabus(String subject, String level) {
        return fetchSyllabus(subject, level, null);
    }

    /**
     * Resolve the active syllabus, preferring sources in this order:
     *   1. {@code learnerSyllabus} — the learner's saved/suggested syllabus
     *      (populated by SyllabusController) wins over everything. This is
     *      what makes "I pasted my syllabus" actually take effect.
     *   2. School LMS via {@code @RemoteSquad} when configured.
     *   3. Built-in default keyed by subject.
     *
     * Returns the chapter list as a single string (the format the
     * planning prompt is already built for) so callers don't change.
     */
    public String fetchSyllabus(String subject, String level, Syllabus learnerSyllabus) {
        if (learnerSyllabus != null && learnerSyllabus.chapters != null
            && !learnerSyllabus.chapters.isBlank()) {
            String marker = (learnerSyllabus.source != null
                ? learnerSyllabus.source : "CUSTOM");
            return "[" + marker + " syllabus]\n" + learnerSyllabus.chapters;
        }
        try {
            return lmsClient.submit("GET_SYLLABUS subject=" + subject + " level=" + level);
        } catch (Exception e) {
            return defaultSyllabus(subject);
        }
    }

    private String defaultSyllabus(String subject) {
        // Minimal built-in syllabus used when LMS is offline
        return switch (subject.toLowerCase()) {
            case "biology"    -> "Cells, Genetics, Ecology, Physiology, Photosynthesis, Respiration, Evolution";
            case "chemistry"  -> "Atoms, Bonding, Reactions, Organic Chemistry, Quantitative, Electrochemistry";
            case "physics"    -> "Mechanics, Waves, Electricity, Magnetism, Nuclear, Thermodynamics";
            case "mathematics"-> "Algebra, Calculus, Statistics, Geometry, Trigonometry, Proof";
            default           -> "Core concepts for " + subject;
        };
    }
}
