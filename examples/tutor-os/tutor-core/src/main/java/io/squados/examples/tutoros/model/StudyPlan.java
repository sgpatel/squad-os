package io.squados.examples.tutoros.model;

import io.squados.annotation.OutputField;

/**
 * Personalised study plan produced by CurriculumPlannerAgent via @AutoPlan.
 *
 * The plan is iterated until all gap concepts are addressed
 * (stopCondition = "ALL_GAPS_COVERED") or maxIterations is reached.
 *
 * Each chapter in the plan maps to a @DurableAgent checkpoint so the
 * student can resume from the exact chapter after a restart.
 */
public class StudyPlan {

    @OutputField(description = "Unique plan ID, e.g. PLAN-2024-Biology-Priya-001")
    public String planId;

    @OutputField(description = "Subject this plan covers", example = "Biology")
    public String subject;

    @OutputField(description = "Topic this plan covers", example = "Photosynthesis")
    public String topic;

    @OutputField(description = "Ordered list of chapter titles as a newline-separated string",
                 example     = "Chapter 1: Overview\nChapter 2: Chloroplast Structure\nChapter 3: Light Reactions")
    public String chapters;

    @OutputField(description = "Index of the chapter the learner should start on (0-based)", example = "2")
    public int currentChapterIndex;

    @OutputField(description = "Estimated total hours to complete the plan", example = "8")
    public int estimatedHours;

    @OutputField(description = "Weekly session target to meet goal in time", example = "3")
    public int weeklySessionTarget;

    @OutputField(description = "Gap concepts that drove the plan design, comma-separated",
                 example     = "proton gradient, Calvin cycle")
    public String targetGaps;

    @OutputField(description = "ISO-8601 date when the plan was generated")
    public String createdAt;

    @OutputField(description = "ISO-8601 target completion date based on sessionsPerWeek", example = "2024-09-30")
    public String targetCompletionDate;
}
