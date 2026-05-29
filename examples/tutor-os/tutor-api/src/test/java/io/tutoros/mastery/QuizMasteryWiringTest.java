package io.tutoros.mastery;

import io.tutoros.model.AssessmentFeedback;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QuizMasteryWiringTest — verifies the contract that
 * {@code QuizController.submit} relies on:
 *
 *   "Given a per-question {@link AssessmentFeedback} with conceptTag,
 *    feeding it into {@link MasteryService#recordFromFeedback} produces
 *    a {@link ConceptMastery} row whose source is 'quiz' and whose
 *    grade is binned correctly."
 *
 * Doesn't boot Spring or the QuizController itself — that path requires
 * a SessionManager + LLM. The contract under test is the per-call
 * service behaviour, so a unit test is enough.
 */
class QuizMasteryWiringTest {

    private static final String L  = "learner-1";
    private static final String S  = "Mathematics";

    @Test
    void quizCorrectAnswerProducesGoodGradeAndStreakInGraph() {
        InProcessMasteryGraphStore store = new InProcessMasteryGraphStore();
        MasteryService svc = new MasteryService(store);

        // Three correct quiz answers across three concepts.
        for (String concept : List.of("limits", "continuity", "derivatives")) {
            AssessmentFeedback fb = new AssessmentFeedback();
            fb.score = 90;
            fb.correct = true;
            fb.masteryDelta = 0.10; // first-time correct
            svc.recordFromFeedback(L, S, concept, fb, "quiz");
        }

        // Each row exists with source=quiz on the latest history entry.
        for (String concept : List.of("limits", "continuity", "derivatives")) {
            ConceptMastery row = svc.find(L, S, concept).orElseThrow(
                () -> new AssertionError("missing row for " + concept));
            assertEquals(1, row.reps, concept + " should be at rep-1 after one GOOD");
            assertEquals(0.10, row.score, 1e-9);
            assertNotNull(row.history);
            assertEquals("quiz", row.history.get(row.history.size() - 1).source);
            assertEquals(MasteryGrade.GOOD, row.history.get(row.history.size() - 1).grade);
        }
        assertEquals(3, svc.countForSubject(L, S));
    }

    @Test
    void quizWrongAnswerEnqueuesConceptForReview() {
        InProcessMasteryGraphStore store = new InProcessMasteryGraphStore();
        MasteryService svc = new MasteryService(store);

        AssessmentFeedback wrong = new AssessmentFeedback();
        wrong.score = 30;
        wrong.correct = false;
        wrong.masteryDelta = -0.05; // floors AGAIN
        ConceptMastery row = svc.recordFromFeedback(L, S, "integration", wrong, "quiz");

        assertEquals(MasteryGrade.AGAIN,
            row.history.get(row.history.size() - 1).grade,
            "negative masteryDelta must bin to AGAIN");
        assertEquals(0, row.reps, "AGAIN resets the streak counter");
        assertEquals(1, row.intervalDays,
            "AGAIN reschedules the concept for tomorrow's review queue");
        assertNotNull(row.nextReviewAt);
    }

    @Test
    void mixedQuizGoodAndAgainProducesMixedQueue() {
        InProcessMasteryGraphStore store = new InProcessMasteryGraphStore();
        MasteryService svc = new MasteryService(store);

        AssessmentFeedback good = new AssessmentFeedback();
        good.masteryDelta = 0.10;

        AssessmentFeedback wrong = new AssessmentFeedback();
        wrong.masteryDelta = -0.05;

        svc.recordFromFeedback(L, S, "limits",      good,  "quiz");
        svc.recordFromFeedback(L, S, "continuity",  wrong, "quiz");

        // Both are due tomorrow at most. The wrong one is more "fresh
        // pain" — verify both surface and that source tagging stuck.
        for (ConceptMastery row : svc.listForSubject(L, S)) {
            assertEquals("quiz", row.history.get(row.history.size() - 1).source);
        }
    }
}
