package io.tutoros.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BookCoachAgentTest — verifies the bits that don't require an LLM:
 * prompt structure, mode dispatch, ground-truth embedding.
 *
 * <p>Each lens has a contract the controller relies on:
 * <ul>
 *   <li>basic / intermediate / advanced must CARRY a question instruction
 *       and a model-answer instruction, so the AssessmentAgent has
 *       something to grade against.</li>
 *   <li>usage / history / future must explicitly say "no question" so
 *       the UI doesn't render a quiz on a context lens.</li>
 *   <li>Every prompt must embed the chapter body verbatim — grounding
 *       is what stops the LLM inventing facts about textbook content
 *       the learner didn't upload.</li>
 *   <li>history and future explicitly OPEN UP beyond the chapter body
 *       (textbooks rarely cover backstory or research frontiers), so
 *       the prompt must invite outside knowledge for those modes only.</li>
 * </ul>
 */
class BookCoachAgentTest {

    private final BookCoachAgent agent = new BookCoachAgent();

    private static final String BOOK     = "Biology 101";
    private static final String CHAPTER  = "Chapter 7: Photosynthesis";
    private static final String BODY     =
        "Photosynthesis is the process by which plants convert light energy " +
        "into chemical energy stored in glucose. The light-dependent reactions " +
        "happen in the thylakoid membrane; the Calvin cycle happens in the stroma.";
    private static final String CONCEPT  = "photosynthesis";
    private static final String LEVEL    = "SENIOR_SCHOOL";

    // ── Mode normalisation ────────────────────────────────────────────

    @Test
    void normalizeModeAcceptsAllSixLenses() {
        for (String m : new String[]{ "basic","intermediate","advanced","usage","history","future" }) {
            assertEquals(m, BookCoachAgent.normalizeMode(m));
            assertEquals(m, BookCoachAgent.normalizeMode(m.toUpperCase()),
                "uppercase should normalise");
            assertEquals(m, BookCoachAgent.normalizeMode("  " + m + "  "),
                "whitespace should be trimmed");
        }
    }

    @Test
    void normalizeModeFallsBackToBasicOnUnknown() {
        assertEquals("basic", BookCoachAgent.normalizeMode(null));
        assertEquals("basic", BookCoachAgent.normalizeMode(""));
        assertEquals("basic", BookCoachAgent.normalizeMode("legendary"));
        assertEquals("basic", BookCoachAgent.normalizeMode("very-advanced"));
    }

    // ── Grounding — every prompt embeds the chapter ──────────────────

    @Test
    void everyLensEmbedsBookTitleChapterTitleAndBodyVerbatim() {
        for (String mode : new String[]{ "basic","intermediate","advanced","usage","history","future" }) {
            String p = agent.buildPrompt(mode, BOOK, CHAPTER, BODY, CONCEPT, LEVEL, 42, 47);
            assertTrue(p.contains(BOOK),     mode + " prompt must embed book title");
            assertTrue(p.contains(CHAPTER),  mode + " prompt must embed chapter title");
            assertTrue(p.contains(BODY),     mode + " prompt must embed chapter body verbatim (grounding)");
            assertTrue(p.contains(CONCEPT),  mode + " prompt must name the target concept");
            assertTrue(p.contains(LEVEL),    mode + " prompt must include the learner level");
        }
    }

    // ── Quiz lenses carry the question + modelAnswer contract ────────

    @Test
    void quizLensesInstructQuestionAndModelAnswer() {
        for (String mode : new String[]{ "basic","intermediate","advanced" }) {
            String p = agent.buildPrompt(mode, BOOK, CHAPTER, BODY, CONCEPT, LEVEL, 1, 10);
            assertTrue(p.contains("question"),
                mode + " must instruct the agent to populate the question field");
            assertTrue(p.contains("modelAnswer"),
                mode + " must instruct the agent to populate modelAnswer");
        }
    }

    @Test
    void basicIsEasyIntermediateIsMediumAdvancedIsHard() {
        assertTrue(agent.buildPrompt("basic", BOOK, CHAPTER, BODY, CONCEPT, LEVEL, 1, 10)
            .contains("\"EASY\""), "basic must pin difficulty = EASY");
        assertTrue(agent.buildPrompt("intermediate", BOOK, CHAPTER, BODY, CONCEPT, LEVEL, 1, 10)
            .contains("\"MEDIUM\""), "intermediate must pin difficulty = MEDIUM");
        assertTrue(agent.buildPrompt("advanced", BOOK, CHAPTER, BODY, CONCEPT, LEVEL, 1, 10)
            .contains("\"HARD\""), "advanced must pin difficulty = HARD");
    }

    // ── Context lenses tell the agent NOT to attach a quiz ──────────

    @Test
    void contextLensesExplicitlyClearQuestionFields() {
        for (String mode : new String[]{ "usage", "history", "future" }) {
            String p = agent.buildPrompt(mode, BOOK, CHAPTER, BODY, CONCEPT, LEVEL, 1, 10);
            // Each context-lens prompt pins the three quiz fields to "".
            assertTrue(p.contains("question     = \"\""),
                mode + " must tell the agent to leave question empty");
            assertTrue(p.contains("modelAnswer  = \"\""),
                mode + " must tell the agent to leave modelAnswer empty");
            assertTrue(p.contains("difficulty   = \"\""),
                mode + " must tell the agent to leave difficulty empty");
        }
    }

    // ── History + future open up beyond the chapter ─────────────────

    @Test
    void historyAndFutureLensesAllowOutsideKnowledge() {
        // These lenses MUST tell the LLM that drawing on wider context
        // is allowed — textbooks rarely include backstory or frontier.
        String hist = agent.buildPrompt("history", BOOK, CHAPTER, BODY, CONCEPT, LEVEL, 1, 10);
        assertTrue(hist.toLowerCase().contains("wider"),
            "history lens must invite wider context");
        assertTrue(hist.contains("Discovery"), "history must structure around Discovery");
        assertTrue(hist.contains("Evolution"), "history must structure around Evolution");

        String fut = agent.buildPrompt("future", BOOK, CHAPTER, BODY, CONCEPT, LEVEL, 1, 10);
        assertTrue(fut.toLowerCase().contains("wider"),
            "future lens must invite wider context");
        assertTrue(fut.contains("Open problems"), "future must structure around Open problems");
        assertTrue(fut.contains("Careers"),       "future must structure around Careers");
    }

    @Test
    void historyAndFutureClearSourcePagesField() {
        // sourcePages should be empty for these modes since the body
        // can extend beyond the chapter — false attribution would be
        // worse than no citation.
        for (String mode : new String[]{ "history", "future" }) {
            String p = agent.buildPrompt(mode, BOOK, CHAPTER, BODY, CONCEPT, LEVEL, 1, 10);
            assertTrue(p.contains("sourcePages  = \"\""),
                mode + " must clear sourcePages — wider context isn't from the chapter");
        }
    }

    // ── Usage lens stays anchored but allows real-world examples ─────

    @Test
    void usageLensStructuresAroundTodayAndCareers() {
        String p = agent.buildPrompt("usage", BOOK, CHAPTER, BODY, CONCEPT, LEVEL, 42, 47);
        assertTrue(p.contains("Today"),
            "usage must structure around present-day uses");
        assertTrue(p.contains("Why it matters"),
            "usage must motivate the concept");
        // Usage IS grounded in the chapter, so sourcePages should be populated.
        assertTrue(p.contains("pp. 42–47"),
            "usage sourcePages must reflect the chapter page range");
    }

    // ── Page range threading ─────────────────────────────────────────

    @Test
    void pageRangeFlowsIntoQuizLensSourcePages() {
        for (String mode : new String[]{ "basic","intermediate","advanced","usage" }) {
            String p = agent.buildPrompt(mode, BOOK, CHAPTER, BODY, CONCEPT, LEVEL, 100, 125);
            assertTrue(p.contains("pp. 100–125"),
                mode + " must thread the chapter page range into the sourcePages field");
        }
    }

    @Test
    void pageRangeOmittedWhenBoundsZero() {
        // Edge case — fallback-bucket chapters may have pageStart=0
        // when extraction couldn't pin them. The prompt should still
        // be valid; the page range just goes unmentioned.
        String p = agent.buildPrompt("basic", BOOK, CHAPTER, BODY, CONCEPT, LEVEL, 0, 0);
        assertFalse(p.contains("pp. 0–0"),
            "zero page bounds must not surface as 'pp. 0–0'");
    }
}
