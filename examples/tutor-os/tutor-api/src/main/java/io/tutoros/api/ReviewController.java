package io.tutoros.api;

import io.tutoros.mastery.ConceptMastery;
import io.tutoros.mastery.MasteryGrade;
import io.tutoros.mastery.MasteryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Review Controller — surfaces the spaced-repetition state held by
 * {@link MasteryService} as REST endpoints the UI can drive directly.
 *
 * Two endpoints, both per (learnerId, subject):
 *
 *   GET  /api/review/queue/{learnerId}/{subject}
 *        Returns concepts due for review now, sorted most-overdue first.
 *
 *   POST /api/review/{learnerId}/{subject}/answer
 *        Records the learner's grade for a single review card and
 *        returns the updated ConceptMastery row (so the UI can show
 *        the new "next due" timestamp without a follow-up GET).
 *
 * The /queue endpoint deliberately does NOT generate or fetch the actual
 * question content — the UI may show concept name only, or run the
 * existing PracticeAgent to materialise a question per concept. Keeping
 * the review queue separate from question generation lets the same
 * surface back any review style (flashcards, MCQ, essay).
 */
@RestController
@RequestMapping("/api/review")
@CrossOrigin(origins = "*")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final MasteryService mastery;

    public ReviewController(MasteryService mastery) {
        this.mastery = mastery;
    }

    // ── DTOs ────────────────────────────────────────────────────────

    /**
     * Wire shape for {@code POST /answer}.
     *
     * {@code grade} accepts the four SM-2 buttons by name —
     * AGAIN / HARD / GOOD / EASY. Anything else falls back to GOOD so
     * a typo doesn't blow the user's review session.
     */
    public record AnswerRequest(
        String concept,
        String grade,
        /** "review" by default — controllers may override (e.g. "quiz"). */
        String source
    ) {}

    /**
     * Compact view of a {@link ConceptMastery} row for the review queue.
     * Carries only the fields the UI needs so the wire payload stays
     * small even on long subjects.
     */
    public record QueueItem(
        String  concept,
        double  score,
        int     intervalDays,
        Instant lastSeenAt,
        Instant nextReviewAt,
        long    overdueMillis
    ) {
        static QueueItem from(ConceptMastery c, Instant now) {
            return new QueueItem(
                c.concept, c.score, c.intervalDays,
                c.lastSeenAt, c.nextReviewAt,
                c.overdueMillis(now));
        }
    }

    // ── /queue ──────────────────────────────────────────────────────

    /**
     * GET /api/review/queue/{learnerId}/{subject}?limit=N
     *
     * Returns up to {@code limit} (default 20) concepts due for review,
     * sorted most-overdue first. Empty list when nothing is due —
     * 200, not 204, so the UI can render "no reviews today" cleanly.
     */
    @GetMapping("/queue/{learnerId}/{subject}")
    public ResponseEntity<List<QueueItem>> queue(
            @PathVariable String learnerId,
            @PathVariable String subject,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {

        Instant now = Instant.now();
        List<QueueItem> items = mastery.dueForReview(learnerId, subject, limit).stream()
            .map(c -> QueueItem.from(c, now))
            .toList();
        return ResponseEntity.ok(items);
    }

    // ── /answer ─────────────────────────────────────────────────────

    /**
     * POST /api/review/{learnerId}/{subject}/answer
     *
     * Records one outcome — the learner answered a card with the given
     * grade. Returns the updated {@link ConceptMastery} so the UI can
     * advance to the next card without re-pulling the queue.
     *
     * Status codes:
     *   200 — ConceptMastery body
     *   400 — empty concept / grade / unknown learner+subject+concept
     */
    @PostMapping("/{learnerId}/{subject}/answer")
    public ResponseEntity<ConceptMastery> answer(
            @PathVariable String learnerId,
            @PathVariable String subject,
            @RequestBody  AnswerRequest body) {

        if (body == null || body.concept == null || body.concept.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        MasteryGrade grade = parseGrade(body.grade);
        String source = (body.source == null || body.source.isBlank()) ? "review" : body.source;

        ConceptMastery updated = mastery.recordOutcome(
            learnerId, subject, body.concept, grade, source);
        log.debug("review answer learner={} subject={} concept={} grade={} → score={} nextDue={}",
            learnerId, subject, body.concept, grade, updated.score, updated.nextReviewAt);
        return ResponseEntity.ok(updated);
    }

    // ── helpers ─────────────────────────────────────────────────────

    /** Lenient parse — typos and case variation default to GOOD. */
    private static MasteryGrade parseGrade(String raw) {
        if (raw == null) return MasteryGrade.GOOD;
        try {
            return MasteryGrade.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException badName) {
            return MasteryGrade.GOOD;
        }
    }
}
