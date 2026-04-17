package io.squados.examples.tutoros.model;

import io.squados.annotation.OutputField;

/**
 * Structured output produced by DiagnosticAgent at the start of every new
 * subject or after 5 sessions (re-diagnostic).
 *
 * SquadOS: @StructuredOutput(schema = LearnerProfile.class)
 *
 * The profile drives every downstream decision:
 *   - CurriculumPlannerAgent uses gapConcepts[] to build the study path
 *   - DirectTutorAgent and SocraticTutorAgent use learningStyle to weight the @Debate
 *   - PracticeAgent uses bloomsLevel to pitch question difficulty
 *   - ContentAgent uses analogyDomain for real-world examples
 */
public class LearnerProfile {

    // ── Who they are ────────────────────────────────────────────────
    @OutputField(description = "Learner's first name")
    public String name;

    @OutputField(
        description = "Learning level classification",
        example     = "MIDDLE_SCHOOL"
    )
    public String level; // PRIMARY | MIDDLE_SCHOOL | SENIOR_SCHOOL | UNIVERSITY | EDUCATOR | PROFESSIONAL

    @OutputField(description = "Profession if level=PROFESSIONAL or EDUCATOR", example = "Software Engineer")
    public String profession;

    @OutputField(description = "Primary subject being studied", example = "Biology")
    public String subject;

    @OutputField(description = "Specific topic within the subject", example = "Photosynthesis")
    public String topic;

    @OutputField(
        description = "Primary learning goal",
        example     = "PASS_EXAMS"
    )
    public String goal; // PASS_EXAMS | DEEP_UNDERSTANDING | QUICK_REVISION | CAREER_UPSKILLING | TEACH_OTHERS | CURIOSITY

    // ── What they know ─────────────────────────────────────────────
    @OutputField(description = "Comma-separated list of concepts the learner has demonstrated mastery of",
                 example     = "chloroplast structure, photolysis, ATP")
    public String masteredConcepts;

    @OutputField(description = "Comma-separated list of concepts with identified gaps",
                 example     = "proton gradient, Calvin cycle, limiting factors")
    public String gapConcepts;

    @OutputField(description = "Overall mastery percentage for the topic 0–100", example = "42")
    public int masteryPercent;

    // ── How they learn ─────────────────────────────────────────────
    @OutputField(
        description = "Dominant learning style inferred from session interactions",
        example     = "VISUAL"
    )
    public String learningStyle; // VISUAL | NARRATIVE | ANALYTICAL | HANDS_ON | SOCRATIC

    @OutputField(
        description = "Blooms Taxonomy level the learner is currently operating at",
        example     = "UNDERSTAND"
    )
    public String bloomsLevel; // REMEMBER | UNDERSTAND | APPLY | ANALYSE | EVALUATE | CREATE

    @OutputField(description = "Domain for real-world analogies that resonate with this learner",
                 example     = "football")
    public String analogyDomain;

    @OutputField(description = "Whether the learner prefers Socratic (question-led) or direct (explanation-led) teaching",
                 example     = "DIRECT")
    public String preferredTeachingStyle; // SOCRATIC | DIRECT | MIXED

    // ── Pace ───────────────────────────────────────────────────────
    @OutputField(description = "Average sessions per week observed so far", example = "3")
    public int sessionsPerWeek;

    @OutputField(description = "Average session duration in minutes", example = "35")
    public int avgSessionMinutes;

    @OutputField(description = "Whether the learner has shown signs of frustration or disengagement", example = "false")
    public boolean atRisk;
}
