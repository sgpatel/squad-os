package io.tutoros.api;

import io.tutoros.mastery.MasteryService;
import io.tutoros.model.AssessmentFeedback;
import io.tutoros.model.PracticeQuestion;
import io.tutoros.model.Quiz;
import io.tutoros.pipeline.PipelineResult;
import io.tutoros.pipeline.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuizController — dedicated quiz generation, submission, and history endpoints.
 *
 * Complements SessionController: quizzes can be generated mid-session (via
 * SessionController) or independently here for standalone quiz mode.
 *
 * Endpoints:
 *   POST /quiz/{learnerId}/{subject}/generate          — generate a fresh quiz
 *   POST /quiz/{learnerId}/{subject}/{quizId}/submit   — grade all answers
 *   GET  /quiz/{learnerId}/{subject}/history           — all past results
 *   GET  /quiz/{learnerId}/{subject}/{quizId}          — single quiz detail
 */
@RestController
@RequestMapping("/quiz")
@CrossOrigin(origins = "*") // tighten in production — matches SessionController
public class QuizController {

    private static final Logger log = LoggerFactory.getLogger(QuizController.class);

    private final SessionManager sessionManager;

    /** Completed quiz results keyed by learnerId:subject. */
    private final Map<String, List<QuizResult>> resultStore = new ConcurrentHashMap<>();

    /** Active (in-progress) quizzes keyed by quizId. */
    private final Map<String, ActiveQuiz> activeQuizStore = new ConcurrentHashMap<>();

    private final MasteryService mastery;

    public QuizController(SessionManager sessionManager, MasteryService mastery) {
        this.sessionManager = sessionManager;
        this.mastery        = mastery;
    }

    // ── Generate ──────────────────────────────────────────────────────────────

    /**
     * POST /quiz/{learnerId}/{subject}/generate
     *
     * Generates a new quiz using QuizAgent (@Benchmark, @AgentMemory).
     * The session must be active (started via SessionController) so that
     * the learner's profile and mastery map are available in SessionState.
     * `sessionId` is composed from (learnerId, subject) using the same
     * format SessionManager uses internally — see `sessionKey()` there.
     */
    @PostMapping("/{learnerId}/{subject}/generate")
    public ResponseEntity<?> generate(
            @PathVariable String learnerId,
            @PathVariable String subject,
            @RequestBody QuizGenerateRequest req) {
        String sessionId = sessionManager.resolveSessionId(learnerId, subject);
        if (sessionId == null) {
            log.info("Quiz generate: no active session for {}:{} — client should /session/start first",
                learnerId, subject);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorBody("SESSION_NOT_FOUND",
                    "No active session for this learner + subject. Start a session first via /session/start.")
            );
        }

        String topic      = req.topic()      == null || req.topic().isBlank() ? "General" : req.topic();
        String difficulty = req.difficulty() == null || req.difficulty().isBlank() ? "MEDIUM" : req.difficulty();
        int    count      = req.questionCount() <= 0 ? 5 : req.questionCount();

        Quiz quiz;
        try {
            quiz = sessionManager.quiz(sessionId, topic, count, difficulty);
        } catch (RuntimeException e) {
            // LLM call blew up (auth, rate limit, parse failure, …) or
            // the agent returned something unparseable. Surface a real
            // message to the client instead of a bare 500.
            log.error("Quiz generate failed for {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                new ErrorBody("QUIZ_GENERATE_FAILED",
                    "Couldn't generate a quiz: " + rootMessage(e))
            );
        }

        if (quiz == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                new ErrorBody("QUIZ_GENERATE_EMPTY",
                    "The quiz agent returned no quiz. Please try again.")
            );
        }

        // Store as active
        activeQuizStore.put(quiz.quizId,
            new ActiveQuiz(quiz, sessionId, Instant.now().toString()));

        return ResponseEntity.ok(new QuizGenerateResponse(
            quiz.quizId,
            quiz.subject,
            quiz.topic,
            quiz.difficulty,
            quiz.questionCount,
            quiz.timeLimitMinutes,
            quiz.totalMarks,
            quiz.bloomsLevelsCovered,
            quiz.targetGaps,
            quiz.questionsJson,
            "Quiz ready — good luck!"
        ));
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    /**
     * POST /quiz/{learnerId}/{subject}/{quizId}/submit
     *
     * Grades all answers using AssessmentAgent (@Benchmark) and stores result.
     * Returns per-question feedback + overall score. learnerId + subject are
     * path params for URL symmetry with the other routes; the quizId alone
     * is enough to locate the active quiz in-memory.
     */
    @PostMapping("/{learnerId}/{subject}/{quizId}/submit")
    public ResponseEntity<?> submit(
            @PathVariable String learnerId,
            @PathVariable String subject,
            @PathVariable String quizId,
            @RequestBody QuizSubmitRequest req) {

        ActiveQuiz active = activeQuizStore.get(quizId);
        if (active == null) {
            return ResponseEntity.notFound().build();
        }

        String sessionId = active.sessionId();
        List<QuestionResult> questionResults = new ArrayList<>();
        int totalScore = 0;
        int maxScore   = 0;
        List<String> masteredConcepts = new ArrayList<>();
        List<String> revisitConcepts  = new ArrayList<>();
        Map<String, String> bloomsBreakdown = new LinkedHashMap<>();

        for (AnswerEntry entry : req.answers()) {
            // Build a PracticeQuestion stub from the submitted answer entry —
            // SessionManager.submitAnswer needs the question metadata to grade.
            PracticeQuestion q = new PracticeQuestion();
            q.question    = entry.question();
            q.answer      = entry.correctAnswer();
            q.conceptTag  = entry.conceptTag();
            q.type        = "SHORT_ANSWER";
            q.difficulty  = active.quiz().difficulty;
            q.bloomsLevel = "APPLY";
            q.marks       = 1;

            PipelineResult pr = sessionManager.submitAnswer(sessionId, q, entry.studentAnswer(), 1);

            // Only FeedbackResult carries the AssessmentFeedback we need
            if (!(pr instanceof PipelineResult.FeedbackResult fr)) {
                // Skip blocked / safe / etc — count as 0 score, no feedback row
                maxScore += 1;
                continue;
            }
            AssessmentFeedback feedback = fr.feedback();

            // score is 0–100 (int); award full mark above 50, partial above 25
            int earned = feedback.score >= 75 ? 1 : (feedback.score >= 50 ? 1 : 0);
            totalScore += earned;
            maxScore   += 1;

            questionResults.add(new QuestionResult(
                entry.question(),
                entry.correctAnswer(),
                entry.studentAnswer(),
                feedback.score,
                feedback.correct,
                feedback.correctParts,
                feedback.incorrectParts,
                feedback.modelAnswer,
                feedback.bloomsDemonstrated,
                feedback.encouragement
            ));

            if (feedback.masteryDelta > 0.05) {
                masteredConcepts.add(entry.conceptTag());
            } else if (feedback.masteryDelta < -0.02) {
                revisitConcepts.add(entry.conceptTag());
            }

            // Feed quiz outcomes into the mastery graph so the SM-2
            // scheduler + review queue see them alongside tutor-turn
            // answers. Best-effort — never fails the submission.
            // source="quiz" lets UI charts split tutor vs quiz history.
            try {
                if (entry.conceptTag() != null && !entry.conceptTag().isBlank()) {
                    mastery.recordFromFeedback(
                        learnerId, subject, entry.conceptTag(), feedback, "quiz");
                }
            } catch (RuntimeException e) {
                log.warn("mastery write from quiz failed (non-fatal): {}", e.toString());
            }

            String bloom = feedback.bloomsDemonstrated == null ? "UNKNOWN" : feedback.bloomsDemonstrated;
            bloomsBreakdown.merge(bloom, "1",
                (a, b) -> String.valueOf(Integer.parseInt(a) + 1));
        }

        double percentScore = maxScore > 0 ? (double) totalScore / maxScore * 100 : 0;
        String grade = percentScore >= 90 ? "A" : percentScore >= 75 ? "B"
                     : percentScore >= 60 ? "C" : percentScore >= 45 ? "D" : "F";
        String encouragement = percentScore >= 80
            ? "Excellent work! You really know this material."
            : percentScore >= 60
            ? "Good effort! A few concepts to brush up on."
            : "Keep going — every attempt builds your knowledge. Let's review the gaps together.";

        // Persist result
        QuizResult result = new QuizResult(
            quizId, active.quiz().subject, active.quiz().topic, active.quiz().difficulty,
            active.quiz().questionCount, totalScore, maxScore,
            Math.round(percentScore * 10.0) / 10.0, grade,
            String.join(", ", masteredConcepts),
            String.join(", ", revisitConcepts),
            bloomsBreakdown, questionResults, Instant.now().toString()
        );
        resultStore.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(result);
        activeQuizStore.remove(quizId);

        return ResponseEntity.ok(new QuizSubmitResponse(
            quizId, totalScore, maxScore, percentScore, grade,
            String.join(", ", masteredConcepts),
            String.join(", ", revisitConcepts),
            encouragement, questionResults, bloomsBreakdown
        ));
    }

    // ── History ───────────────────────────────────────────────────────────────

    /**
     * GET /quiz/{learnerId}/{subject}/history
     * Returns all completed quiz results for this session,
     * ordered most recent first.
     */
    @GetMapping("/{learnerId}/{subject}/history")
    public ResponseEntity<QuizHistoryResponse> history(
            @PathVariable String learnerId,
            @PathVariable String subject) {
        // If no session has been started yet, there are simply no results
        // — return an empty history rather than 404'ing.
        String sessionId = sessionManager.resolveSessionId(learnerId, subject);
        List<QuizResult> results = sessionId == null
            ? Collections.emptyList()
            : resultStore.getOrDefault(sessionId, Collections.emptyList());

        // Sort most recent first
        List<QuizResult> sorted = results.stream()
            .sorted(Comparator.comparing(QuizResult::completedAt).reversed())
            .toList();

        double avgScore = sorted.stream()
            .mapToDouble(QuizResult::percentScore)
            .average().orElse(0.0);

        int totalAttempts = sorted.size();
        double bestScore = sorted.stream()
            .mapToDouble(QuizResult::percentScore)
            .max().orElse(0.0);

        // Score trend: last 5
        List<Double> scoreTrend = sorted.stream()
            .limit(5)
            .map(QuizResult::percentScore)
            .toList();

        return ResponseEntity.ok(new QuizHistoryResponse(
            sessionId, totalAttempts,
            Math.round(avgScore * 10.0) / 10.0,
            Math.round(bestScore * 10.0) / 10.0,
            scoreTrend, sorted
        ));
    }

    /**
     * GET /quiz/{learnerId}/{subject}/{quizId}
     * Returns a single completed quiz result.
     */
    @GetMapping("/{learnerId}/{subject}/{quizId}")
    public ResponseEntity<?> getResult(
            @PathVariable String learnerId,
            @PathVariable String subject,
            @PathVariable String quizId) {
        String sessionId = sessionManager.resolveSessionId(learnerId, subject);
        if (sessionId == null) return ResponseEntity.notFound().build();

        return resultStore.getOrDefault(sessionId, Collections.emptyList()).stream()
            .filter(r -> r.quizId().equals(quizId))
            .findFirst()
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Public method for ProgressController integration ─────────────────────

    /**
     * Called by SessionController when a quiz is generated mid-session
     * and the result should be recorded for progress analytics.
     */
    public void recordResult(String sessionId, QuizResult result) {
        resultStore.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(result);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /** Walk to the root cause so error bodies show the real reason, not a wrapper. */
    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        String m = cur.getMessage();
        return (m == null || m.isBlank()) ? cur.getClass().getSimpleName() : m;
    }

    /** Structured error body so the UI can branch on `code` rather than parse text. */
    public record ErrorBody(String code, String message) {}

    // ── Inner types ───────────────────────────────────────────────────────────

    public record QuizGenerateRequest(String topic, String difficulty, int questionCount) {}

    public record QuizGenerateResponse(
        String quizId, String subject, String topic, String difficulty,
        int questionCount, int timeLimitMinutes, int totalMarks,
        String bloomsLevelsCovered, String targetGaps,
        String questionsJson, String message) {}

    public record AnswerEntry(String question, String correctAnswer, String studentAnswer, String conceptTag) {}

    public record QuizSubmitRequest(List<AnswerEntry> answers) {}

    public record QuestionResult(
        String question, String correctAnswer, String studentAnswer,
        double score, boolean correct,
        String correctParts, String incorrectParts, String modelAnswer,
        String bloomsDemonstrated, String encouragement) {}

    public record QuizSubmitResponse(
        String quizId, int totalScore, int maxScore, double percentScore, String grade,
        String masteredConcepts, String revisitConcepts, String encouragement,
        List<QuestionResult> questionResults, Map<String, String> bloomsBreakdown) {}

    public record QuizResult(
        String quizId, String subject, String topic, String difficulty,
        int questionCount, int totalScore, int maxScore, double percentScore, String grade,
        String masteredConcepts, String revisitConcepts,
        Map<String, String> bloomsBreakdown, List<QuestionResult> questionResults,
        String completedAt) {}

    public record QuizHistoryResponse(
        String sessionId, int totalAttempts,
        double avgScore, double bestScore,
        List<Double> scoreTrend, List<QuizResult> results) {}

    public record ActiveQuiz(Quiz quiz, String sessionId, String startedAt) {}
}
