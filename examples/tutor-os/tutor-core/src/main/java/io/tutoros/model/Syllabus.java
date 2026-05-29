package io.tutoros.model;

import io.squados.annotation.OutputField;

/**
 * Syllabus — the canonical chapter-list-with-bullets for a (subject, topic,
 * level) triple. Sits BELOW {@link StudyPlan} in the planning stack:
 *
 *   Syllabus  = "what should be taught"  (chapters + sub-points, rough scope)
 *   StudyPlan = "how this learner studies it" (ordering + pacing + gap-fill)
 *
 * Two production sources:
 *   1. {@link io.tutoros.agent.SyllabusSuggesterAgent} generates one when the
 *      learner clicks "Suggest a syllabus".
 *   2. The learner pastes their own (e.g. their school's) syllabus through
 *      the /api/syllabus/save endpoint.
 *
 * Either way the active syllabus is stored on {@link LearnerProfile} and
 * read by {@link io.tutoros.agent.CurriculumPlannerAgent#fetchSyllabus} so
 * that the StudyPlan respects what the learner actually has to cover —
 * solving the cross-subject content drift that PR-A's runtime gate alone
 * can't prevent (e.g. a Maths session that was given a free-form syllabus
 * by the learner stays inside it).
 *
 * Storage shape:
 *   - {@link #chapters} is a single newline-separated string with optional
 *     "- " bullet sub-lines under each chapter title. We keep it as one
 *     String (not a list) because @StructuredOutput's schema generator
 *     handles primitives reliably across LLMs; a nested List<Chapter>
 *     parses worse on weaker models.
 *
 * Example chapters string:
 *   <pre>
 *   Chapter 1: Introduction to limits
 *   - Intuition: what does "approaches" mean
 *   - One-sided vs two-sided limits
 *   - Limits at infinity
 *   Chapter 2: Continuity
 *   - Three conditions for continuity
 *   - Removable vs jump discontinuities
 *   </pre>
 */
public class Syllabus {

    @OutputField(
        description = "Subject this syllabus covers — must echo the input subject exactly",
        example     = "Mathematics"
    )
    public String subject;

    @OutputField(
        description = "Topic within the subject — must echo the input topic exactly",
        example     = "Calculus — limits and continuity"
    )
    public String topic;

    @OutputField(
        description = "Learner level the syllabus is calibrated for. " +
                      "One of: PRIMARY | MIDDLE_SCHOOL | SENIOR_SCHOOL | " +
                      "UNIVERSITY | EDUCATOR | PROFESSIONAL.",
        example     = "SENIOR_SCHOOL"
    )
    public String level;

    @OutputField(
        description = "Chapter titles with bulleted sub-points, newline-separated. " +
                      "Each chapter starts with 'Chapter N: <title>' and is followed " +
                      "by 2–5 lines starting with '- '. Aim for 4–8 chapters total. " +
                      "Stay strictly inside the stated subject/topic — do not drift " +
                      "into adjacent subjects.",
        example     = "Chapter 1: Introduction to limits\n- Intuition: what does \"approaches\" mean\n- One-sided vs two-sided limits"
    )
    public String chapters;

    @OutputField(
        description = "One-sentence rationale: why this scope/depth fits the learner's level",
        example     = "Sequencing intuition before formal definitions matches SENIOR_SCHOOL pacing."
    )
    public String rationale;

    @OutputField(
        description = "Source of the syllabus. SUGGESTED = produced by SyllabusSuggesterAgent; " +
                      "CUSTOM = pasted by the learner; LMS = fetched from the school LMS.",
        example     = "SUGGESTED"
    )
    public String source;
}
