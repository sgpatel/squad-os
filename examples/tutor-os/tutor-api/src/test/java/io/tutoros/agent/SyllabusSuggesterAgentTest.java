package io.tutoros.agent;

import io.tutoros.model.LearnerProfile;
import io.tutoros.model.Syllabus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SyllabusSuggesterAgentTest — verifies the bits that don't require a
 * live LLM:
 *
 *   1. The suggest prompt embeds the subject, topic, and level exactly,
 *      so the agent can't drift to an adjacent subject during PR-A's
 *      strict segregation.
 *   2. CurriculumPlannerAgent.fetchSyllabus prefers a learner-supplied
 *      Syllabus over the LMS / built-in default — the whole point of
 *      /api/syllabus/save.
 *   3. LearnerProfile carries the customSyllabus field and can be
 *      round-tripped without losing it.
 */
class SyllabusSuggesterAgentTest {

    @Test
    void promptEmbedsSubjectTopicAndLevelExactly() {
        SyllabusSuggesterAgent agent = new SyllabusSuggesterAgent();
        String prompt = agent.suggestPrompt(
            "Mathematics", "Calculus — limits and continuity", "SENIOR_SCHOOL");

        // Every input must show up verbatim in the LLM prompt — that's the
        // contract that lets PR-A's verification compare echoes.
        assertTrue(prompt.contains("Mathematics"),
            "subject must be embedded in prompt");
        assertTrue(prompt.contains("Calculus — limits and continuity"),
            "topic must be embedded verbatim (no truncation, no rewording)");
        assertTrue(prompt.contains("SENIOR_SCHOOL"),
            "level must be embedded so depth calibration kicks in");

        // Hard-rule fragments we rely on for stay-in-scope behaviour.
        assertTrue(prompt.contains("STRICTLY"),
            "prompt must instruct the LLM to stay strictly inside the subject");
        assertTrue(prompt.contains("4–8 chapters"),
            "chapter count guidance must be present");
        assertTrue(prompt.contains("\"SUGGESTED\""),
            "source contract must be pinned to SUGGESTED");
    }

    @Test
    void promptHandlesMissingFieldsGracefully() {
        // Defensive check — controller may pass nulls if the request body
        // somehow reaches the agent without scope resolution. The prompt
        // should still be a valid string, not an NPE.
        SyllabusSuggesterAgent agent = new SyllabusSuggesterAgent();
        String prompt = agent.suggestPrompt(null, null, null);
        assertNotNull(prompt);
        assertTrue(prompt.contains("(unknown)"));
        assertTrue(prompt.contains("MIDDLE_SCHOOL"),
            "level must default to MIDDLE_SCHOOL when caller passes null");
    }

    @Test
    void plannerPrefersLearnerSyllabusOverBuiltInDefault() {
        CurriculumPlannerAgent planner = new CurriculumPlannerAgent();

        Syllabus learnerSyllabus = new Syllabus();
        learnerSyllabus.subject  = "Mathematics";
        learnerSyllabus.topic    = "Trigonometry";
        learnerSyllabus.level    = "SENIOR_SCHOOL";
        learnerSyllabus.chapters = "Chapter 1: Unit circle\n- sin/cos definitions\n- radians";
        learnerSyllabus.source   = "CUSTOM";

        String resolved = planner.fetchSyllabus("Mathematics", "SENIOR_SCHOOL", learnerSyllabus);
        assertTrue(resolved.contains("[CUSTOM syllabus]"),
            "resolved string must label the source as CUSTOM");
        assertTrue(resolved.contains("Unit circle"),
            "resolved string must be the learner's chapters, not the default");
        assertFalse(resolved.contains("Algebra, Calculus, Statistics"),
            "default Mathematics syllabus must NOT leak through when learner supplied one");
    }

    @Test
    void plannerFallsBackToBuiltInWhenLearnerSyllabusAbsent() {
        CurriculumPlannerAgent planner = new CurriculumPlannerAgent();
        // No LMS client → exception in lmsClient.submit → falls back to defaults.
        String resolved = planner.fetchSyllabus("Mathematics", "SENIOR_SCHOOL", null);
        assertNotNull(resolved);
        assertTrue(resolved.toLowerCase().contains("calculus")
                || resolved.toLowerCase().contains("trigonometry"),
            "built-in Mathematics default must be used when nothing else is available");
    }

    @Test
    void learnerProfileCarriesCustomSyllabus() {
        // PR-A relies on the per-turn TurnContext reading subject/topic from
        // the profile. PR-B adds customSyllabus next to those fields. Make
        // sure the field is reachable and round-trippable.
        LearnerProfile p = new LearnerProfile();
        p.subject = "Mathematics";
        p.topic   = "Trigonometry";

        Syllabus s = new Syllabus();
        s.subject  = "Mathematics";
        s.topic    = "Trigonometry";
        s.chapters = "Chapter 1: Unit circle";
        s.source   = "CUSTOM";

        p.customSyllabus = s;
        assertSame(s, p.customSyllabus);
        assertEquals("Mathematics", p.customSyllabus.subject);
    }
}
