package io.tutoros.api;

import io.tutoros.mastery.ConceptMastery;
import io.tutoros.mastery.MasteryService;
import io.tutoros.model.*;
import io.tutoros.pipeline.SessionManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Progress Controller — full learning analytics for a learner.
 *
 * Feeds the Progress screen in the UI:
 *   - Overall mastery percentage per topic
 *   - Concept-level mastery with trend (improving/plateau/declining)
 *   - Session history with expandable summaries
 *   - Learning streak (consecutive days with a session)
 *   - Bloom's level progression over time
 *   - Quiz performance history
 *   - Comparison to goal (on track / behind / ahead)
 *   - Teacher-visible report (parent note aggregation)
 *
 * Endpoints:
 *   GET  /progress/{learnerId}                         — dashboard summary
 *   GET  /progress/{learnerId}/{subject}               — subject-level progress
 *   GET  /progress/{learnerId}/{subject}/concepts      — concept mastery grid
 *   GET  /progress/{learnerId}/{subject}/sessions      — session history
 *   GET  /progress/{learnerId}/{subject}/streak        — streak + calendar
 *   GET  /progress/{learnerId}/{subject}/blooms        — Bloom's progression
 *   GET  /progress/{learnerId}/{subject}/quiz-history  — quiz scores over time
 *   GET  /progress/{learnerId}/{subject}/report        — teacher/parent report
 *   GET  /progress/{learnerId}/{subject}/goals         — goal tracking
 */
@RestController
@RequestMapping("/progress")
@CrossOrigin(origins = "*")
public class ProgressController {

    private final SessionManager sessionManager;
    /**
     * Source of truth for per-concept mastery as of M3-A. Reads now go
     * through this; the legacy {@link #masteryTimeline} map is kept as a
     * write-through cache for any caller that still uses
     * {@link #recordMastery} so its trend calculation keeps working
     * during the transition.
     */
    private final MasteryService mastery;

    /** In-memory stores — production uses a time-series DB */
    private final Map<String, List<SessionSummary>>       sessionHistory  = new HashMap<>();
    private final Map<String, Map<String, List<Double>>>  masteryTimeline = new HashMap<>();
    private final Map<String, List<QuizRecord>>           quizHistory     = new HashMap<>();
    private final Map<String, List<LocalDate>>            sessionDates    = new HashMap<>();

    public ProgressController(SessionManager sessionManager, MasteryService mastery) {
        this.sessionManager = sessionManager;
        this.mastery        = mastery;
    }

    // ── Dashboard summary ─────────────────────────────────────────────

    /**
     * GET /progress/{learnerId}
     *
     * Cross-subject dashboard: total sessions, overall mastery,
     * streak, top performing subject, weakest concept.
     */
    @GetMapping("/{learnerId}")
    public ResponseEntity<DashboardSummary> getDashboard(@PathVariable String learnerId) {
        return ResponseEntity.ok(new DashboardSummary(
            learnerId,
            totalSessions(learnerId),
            overallMasteryAcrossSubjects(learnerId),
            calculateStreak(learnerId),
            avgQuizScore(learnerId),
            topSubject(learnerId),
            weakestConcept(learnerId)
        ));
    }

    // ── Subject-level progress ────────────────────────────────────────

    /**
     * GET /progress/{learnerId}/{subject}
     *
     * Full progress breakdown for one subject:
     *   - overall mastery %
     *   - chapter completion
     *   - total study time
     *   - on-track status vs goal
     */
    @GetMapping("/{learnerId}/{subject}")
    public ResponseEntity<SubjectProgress> getSubjectProgress(
            @PathVariable String learnerId,
            @PathVariable String subject) {

        SessionManager.ProgressSnapshot snap =
            sessionManager.getProgress(learnerId, subject);

        return ResponseEntity.ok(new SubjectProgress(
            subject,
            (int) Math.round(snap.overallMastery() * 100),
            snap.sessionCount(),
            snap.currentChapter(),
            estimatedTotalSessions(learnerId, subject),
            goalStatus(snap),
            totalStudyMinutes(learnerId, subject)
        ));
    }

    // ── Concept mastery grid ──────────────────────────────────────────

    /**
     * GET /progress/{learnerId}/{subject}/concepts
     *
     * Returns a list of concepts with:
     *   - current mastery 0–100
     *   - trend: IMPROVING | STABLE | DECLINING | PLATEAU
     *   - sessions studied
     *   - last attempt date
     *
     * Used to render the mastery grid on the Progress screen.
     */
    @GetMapping("/{learnerId}/{subject}/concepts")
    public ResponseEntity<ConceptMasteryResponse> getConceptMastery(
            @PathVariable String learnerId,
            @PathVariable String subject) {

        // Prefer the MasteryService graph (M3-A) — the answer-submission
        // path writes there now, so it's the live source of truth. Fall
        // back to the legacy in-memory timeline only when the graph is
        // empty for this learner+subject (e.g. demo mode, or a fresh
        // boot with no answers yet).
        List<ConceptMasteryItem> items = new ArrayList<>();

        for (ConceptMastery row : mastery.listForSubject(learnerId, subject)) {
            int currentPct = (int) Math.round(row.score * 100);
            items.add(new ConceptMasteryItem(
                row.concept, currentPct,
                computeTrendFromHistory(row),
                row.history != null ? row.history.size() : 0,
                row.lastSeenAt != null
                    ? row.lastSeenAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                    : "Today"
            ));
        }

        // Legacy fallback — keeps existing demo behaviour while the
        // graph warms up. Removed in a follow-up once the answer-write
        // path has been live long enough to backfill all subjects.
        if (items.isEmpty()) {
            String key = progressKey(learnerId, subject);
            Map<String, List<Double>> timeline = masteryTimeline.getOrDefault(key, Map.of());
            timeline.forEach((concept, history) -> {
                int currentPct = history.isEmpty() ? 0
                                 : (int) Math.round(history.getLast() * 100);
                items.add(new ConceptMasteryItem(
                    concept, currentPct,
                    computeTrend(history),
                    history.size(),
                    "Today"
                ));
            });
        }

        // Seed realistic data if STILL empty (demo mode, no real data anywhere)
        if (items.isEmpty()) items.addAll(seedConceptData(subject));

        // Sort by mastery ascending (weakest first — most actionable)
        items.sort(Comparator.comparingInt(ConceptMasteryItem::masteryPct));

        return ResponseEntity.ok(new ConceptMasteryResponse(subject, items));
    }

    /**
     * Trend over the last few mastery history entries. Uses the same
     * thresholds as {@link #computeTrend(List)} so UI labels stay stable
     * regardless of which read path produced the row.
     */
    private String computeTrendFromHistory(ConceptMastery row) {
        if (row.history == null || row.history.size() < 2) return "STABLE";
        List<Double> scores = new ArrayList<>(row.history.size());
        for (var h : row.history) scores.add(h.score);
        return computeTrend(scores);
    }

    // ── Session history ───────────────────────────────────────────────

    /**
     * GET /progress/{learnerId}/{subject}/sessions
     *
     * Ordered list of all past sessions (newest first).
     * Each item includes: date, chapter, score, duration, key insight, todos generated.
     *
     * UI: clicking an item expands the full summary card.
     */
    @GetMapping("/{learnerId}/{subject}/sessions")
    public ResponseEntity<SessionHistoryResponse> getSessionHistory(
            @PathVariable String learnerId,
            @PathVariable String subject) {

        String key = progressKey(learnerId, subject);
        List<SessionSummary> summaries = sessionHistory.getOrDefault(key,
            seedSessionHistory(subject)); // demo data if empty

        List<SessionHistoryItem> items = summaries.stream()
            .map(s -> new SessionHistoryItem(
                s.sessionId, s.sessionDate,
                s.chapterCovered, s.sessionScore,
                s.durationMinutes, s.keyInsight,
                s.conceptsMastered, s.revisitConcepts,
                s.masteryGained, s.teachingStyleUsed,
                s.generatedTodos, s.parentNote
            )).toList();

        return ResponseEntity.ok(new SessionHistoryResponse(subject, items));
    }

    // ── Streak calendar ───────────────────────────────────────────────

    /**
     * GET /progress/{learnerId}/{subject}/streak
     *
     * Returns:
     *   - currentStreak: consecutive days with ≥1 session
     *   - longestStreak: personal best
     *   - last30Days: list of {date, hasSession} for the calendar widget
     */
    @GetMapping("/{learnerId}/{subject}/streak")
    public ResponseEntity<StreakResponse> getStreak(
            @PathVariable String learnerId,
            @PathVariable String subject) {

        int streak = calculateStreak(learnerId);
        List<StreakDay> last30 = buildStreakCalendar(learnerId, subject);

        return ResponseEntity.ok(new StreakResponse(
            streak, Math.max(streak, 7), // longestStreak ≥ current
            last30
        ));
    }

    // ── Bloom's progression ───────────────────────────────────────────

    /**
     * GET /progress/{learnerId}/{subject}/blooms
     *
     * Shows how the learner's demonstrated Bloom's level has advanced
     * over their sessions — a key indicator of deep learning.
     *
     * Returns a timeline: [{sessionNumber, bloomsLevel, sessionDate}]
     */
    @GetMapping("/{learnerId}/{subject}/blooms")
    public ResponseEntity<BloomsProgressionResponse> getBloomsProgression(
            @PathVariable String learnerId,
            @PathVariable String subject) {

        // Production: read from ProgressAgent memory records
        List<BloomsDataPoint> points = List.of(
            new BloomsDataPoint(1, "REMEMBER",   "Session 1"),
            new BloomsDataPoint(2, "REMEMBER",   "Session 2"),
            new BloomsDataPoint(3, "UNDERSTAND", "Session 3"),
            new BloomsDataPoint(4, "UNDERSTAND", "Session 4"),
            new BloomsDataPoint(5, "APPLY",      "Session 5"),
            new BloomsDataPoint(6, "APPLY",      "Session 6"),
            new BloomsDataPoint(7, "ANALYSE",    "Session 7 (current)")
        );

        return ResponseEntity.ok(new BloomsProgressionResponse(subject, points,
            "ANALYSE", "You've progressed 4 Bloom's levels in 7 sessions — excellent!"));
    }

    // ── Quiz history ──────────────────────────────────────────────────

    /**
     * GET /progress/{learnerId}/{subject}/quiz-history
     *
     * Quiz scores over time with per-concept breakdown.
     * Used for the quiz performance chart on the Progress screen.
     */
    @GetMapping("/{learnerId}/{subject}/quiz-history")
    public ResponseEntity<QuizHistoryResponse> getQuizHistory(
            @PathVariable String learnerId,
            @PathVariable String subject) {

        String key = progressKey(learnerId, subject);
        List<QuizRecord> records = quizHistory.getOrDefault(key,
            seedQuizHistory(subject));

        double avg = records.stream().mapToInt(QuizRecord::score).average().orElse(0);

        return ResponseEntity.ok(new QuizHistoryResponse(
            subject, records, (int) Math.round(avg),
            bestQuiz(records), latestTrend(records)
        ));
    }

    // ── Goals tracking ────────────────────────────────────────────────

    /**
     * GET /progress/{learnerId}/{subject}/goals
     *
     * How the learner is tracking against their stated goal:
     *   PASS_EXAMS        → % of syllabus covered, estimated exam readiness
     *   DEEP_UNDERSTANDING→ Bloom's level reached vs target
     *   QUICK_REVISION    → key concepts revised vs total
     *   CAREER_UPSKILLING → practical exercises completed
     */
    @GetMapping("/{learnerId}/{subject}/goals")
    public ResponseEntity<GoalTrackingResponse> getGoalTracking(
            @PathVariable String learnerId,
            @PathVariable String subject) {

        SessionManager.ProgressSnapshot snap =
            sessionManager.getProgress(learnerId, subject);

        return ResponseEntity.ok(new GoalTrackingResponse(
            subject,
            snap.overallMastery() >= 0.7 ? "ON_TRACK" : "BEHIND",
            (int)(snap.overallMastery() * 100),
            snap.sessionCount() >= 3 ? "You're progressing well." :
                "Start more sessions to build momentum.",
            estimatedWeeksToGoal(snap)
        ));
    }

    // ── Teacher / parent report ───────────────────────────────────────

    /**
     * GET /progress/{learnerId}/{subject}/report
     *
     * Aggregated parent/teacher report from the last 5 session parentNotes.
     * Suitable for sharing at parent-teacher meetings or in portal notifications.
     */
    @GetMapping("/{learnerId}/{subject}/report")
    public ResponseEntity<TeacherReportResponse> getTeacherReport(
            @PathVariable String learnerId,
            @PathVariable String subject) {

        String key = progressKey(learnerId, subject);
        List<SessionSummary> summaries = sessionHistory.getOrDefault(key,
            seedSessionHistory(subject));

        List<String> parentNotes = summaries.stream()
            .filter(s -> s.parentNote != null && !s.parentNote.isBlank())
            .map(s -> "[" + s.sessionDate + "] " + s.parentNote)
            .limit(5).toList();

        int avgScore = summaries.stream()
            .mapToInt(s -> s.sessionScore).sum() / Math.max(1, summaries.size());

        return ResponseEntity.ok(new TeacherReportResponse(
            learnerId, subject,
            avgScore,
            summaries.size(),
            summaries.stream().anyMatch(s -> s.distressSignalDetected),
            parentNotes,
            "Learner is making consistent progress. Focus area: " +
            summaries.stream().map(s -> s.revisitConcepts)
                     .filter(s -> s != null && !s.isBlank())
                     .findFirst().orElse("none identified yet") + "."
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String progressKey(String id, String subject) {
        return id + ":" + subject.toLowerCase().replace(" ", "-");
    }

    private int totalSessions(String id) {
        return sessionHistory.values().stream()
            .mapToInt(List::size).sum();
    }

    private int overallMasteryAcrossSubjects(String id) {
        return 68; // production: aggregate from all subject progress snapshots
    }

    private int calculateStreak(String id) {
        return 7; // production: count consecutive session dates
    }

    private int avgQuizScore(String id) { return 84; }
    private String topSubject(String id) { return "Biology"; }
    private String weakestConcept(String id) { return "Proton Gradient"; }

    private int estimatedTotalSessions(String id, String subject) { return 18; }
    private int totalStudyMinutes(String id, String subject) { return 480; }
    private int estimatedWeeksToGoal(SessionManager.ProgressSnapshot snap) {
        return snap.overallMastery() >= 0.85 ? 0
               : (int) Math.ceil((1.0 - snap.overallMastery()) / 0.08);
    }

    private String goalStatus(SessionManager.ProgressSnapshot snap) {
        double m = snap.overallMastery();
        if (m >= 0.85) return "ACHIEVED";
        if (m >= 0.60) return "ON_TRACK";
        if (m >= 0.40) return "BEHIND";
        return "JUST_STARTED";
    }

    private String computeTrend(List<Double> history) {
        if (history.size() < 2) return "STABLE";
        double delta = history.getLast() - history.get(history.size() - 2);
        if (delta >  0.05) return "IMPROVING";
        if (delta < -0.03) return "DECLINING";
        if (history.size() >= 3) {
            double totalDelta = history.getLast() - history.get(history.size() - 3);
            if (Math.abs(totalDelta) < 0.05) return "PLATEAU";
        }
        return "STABLE";
    }

    private List<StreakDay> buildStreakCalendar(String id, String subject) {
        List<StreakDay> days = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            days.add(new StreakDay(d.toString(), i < 7)); // last 7 days = had session
        }
        return days;
    }

    private int bestQuiz(List<QuizRecord> records) {
        return records.stream().mapToInt(QuizRecord::score).max().orElse(0);
    }

    private String latestTrend(List<QuizRecord> records) {
        if (records.size() < 2) return "STABLE";
        int diff = records.getLast().score() - records.get(records.size() - 2).score();
        return diff > 5 ? "IMPROVING" : diff < -5 ? "DECLINING" : "STABLE";
    }

    // ── Demo seed data ────────────────────────────────────────────────

    private List<ConceptMasteryItem> seedConceptData(String subject) {
        return List.of(
            new ConceptMasteryItem("Chloroplast Structure",    92, "STABLE",    4, "2 days ago"),
            new ConceptMasteryItem("Photolysis",               85, "IMPROVING", 3, "Yesterday"),
            new ConceptMasteryItem("ATP Synthesis",            78, "IMPROVING", 4, "Yesterday"),
            new ConceptMasteryItem("Light Reactions Overall",  68, "IMPROVING", 5, "Today"),
            new ConceptMasteryItem("Proton Gradient",          42, "PLATEAU",   3, "Today"),
            new ConceptMasteryItem("Calvin Cycle",             12, "STABLE",    1, "4 days ago")
        );
    }

    private List<SessionSummary> seedSessionHistory(String subject) {
        SessionSummary s1 = new SessionSummary();
        s1.sessionId = "SESSION-001"; s1.sessionDate = "2026-04-13";
        s1.chapterCovered = "Light-Dependent Reactions"; s1.sessionScore = 82;
        s1.durationMinutes = 45; s1.masteryGained = 8;
        s1.conceptsMastered = "ATP, NADPH, Photolysis";
        s1.revisitConcepts = "Proton gradient, Cyclic photophosphorylation";
        s1.keyInsight = "Dam analogy unlocked proton gradient concept";
        s1.teachingStyleUsed = "DIRECT";
        s1.generatedTodos = "Draw Z-scheme\nPractice proton gradient questions";
        s1.parentNote = "Priya had a productive session on light reactions. Strong progress on ATP synthesis.";
        s1.distressSignalDetected = false;

        SessionSummary s2 = new SessionSummary();
        s2.sessionId = "SESSION-002"; s2.sessionDate = "2026-04-11";
        s2.chapterCovered = "Chloroplast Structure"; s2.sessionScore = 91;
        s2.durationMinutes = 38; s2.masteryGained = 15;
        s2.conceptsMastered = "Thylakoid, Grana, Stroma";
        s2.revisitConcepts = "";
        s2.keyInsight = "Visual diagram of stacked grana clicked instantly";
        s2.teachingStyleUsed = "DIRECT";
        s2.generatedTodos = "Label blank chloroplast diagram";
        s2.parentNote = "Excellent session. Mastered chloroplast structure. Ready to advance.";
        s2.distressSignalDetected = false;

        return List.of(s1, s2);
    }

    private List<QuizRecord> seedQuizHistory(String subject) {
        return List.of(
            new QuizRecord("QUIZ-001", "2026-04-13", "Light Reactions",    82, 5, "MEDIUM"),
            new QuizRecord("QUIZ-002", "2026-04-11", "Chloroplast Structure", 90, 5, "MEDIUM"),
            new QuizRecord("QUIZ-003", "2026-04-09", "Photosynthesis Overview", 74, 5, "EASY"),
            new QuizRecord("QUIZ-004", "2026-04-07", "Photosynthesis Overview", 68, 5, "EASY")
        );
    }

    /** Store a session summary (called by SessionManager after endSession) */
    public void recordSession(String learnerId, String subject, SessionSummary summary) {
        String key = progressKey(learnerId, subject);
        sessionHistory.computeIfAbsent(key, k -> new ArrayList<>()).add(0, summary);
        sessionDates.computeIfAbsent(learnerId, k -> new ArrayList<>())
                    .add(LocalDate.now());
    }

    /** Store a quiz result (called by QuizController after quiz completion) */
    public void recordQuiz(String learnerId, String subject, QuizRecord record) {
        quizHistory.computeIfAbsent(progressKey(learnerId, subject),
                                    k -> new ArrayList<>()).add(0, record);
    }

    /**
     * Store mastery update — legacy entry point. Kept so callers that
     * pre-date M3-A's MasteryService keep compiling, but the canonical
     * write path is now {@code SessionController.submitAnswer} →
     * {@link MasteryService#recordFromFeedback}, which feeds the
     * persistent graph this controller reads from.
     *
     * Writes here continue to populate the legacy timeline so any
     * caller that hasn't migrated still sees its history.
     */
    public void recordMastery(String learnerId, String subject,
                               String concept, double masteryScore) {
        masteryTimeline
            .computeIfAbsent(progressKey(learnerId, subject), k -> new LinkedHashMap<>())
            .computeIfAbsent(concept, k -> new ArrayList<>())
            .add(masteryScore);
    }

    // ── Request / Response records ────────────────────────────────────

    public record DashboardSummary(
        String learnerId, int totalSessions, int overallMasteryPct,
        int streakDays, int avgQuizScore, String topSubject, String weakestConcept
    ) {}

    public record SubjectProgress(
        String subject, int masteryPct, int sessionsCompleted,
        String currentChapter, int estimatedTotalSessions,
        String goalStatus, int totalStudyMinutes
    ) {}

    public record ConceptMasteryItem(
        String concept, int masteryPct, String trend,
        int sessionsStudied, String lastAttempt
    ) {}

    public record ConceptMasteryResponse(String subject, List<ConceptMasteryItem> concepts) {}

    public record SessionHistoryItem(
        String sessionId, String date, String chapter, int score,
        int durationMinutes, String keyInsight, String conceptsMastered,
        String revisitConcepts, int masteryGained, String teachingStyle,
        String todos, String parentNote
    ) {}

    public record SessionHistoryResponse(String subject, List<SessionHistoryItem> sessions) {}

    public record StreakDay(String date, boolean hasSession) {}

    public record StreakResponse(int currentStreak, int longestStreak, List<StreakDay> last30Days) {}

    public record BloomsDataPoint(int sessionNumber, String bloomsLevel, String label) {}

    public record BloomsProgressionResponse(
        String subject, List<BloomsDataPoint> timeline,
        String currentLevel, String insight
    ) {}

    public record QuizRecord(
        String quizId, String date, String topic,
        int score, int questionCount, String difficulty
    ) {}

    public record QuizHistoryResponse(
        String subject, List<QuizRecord> quizzes,
        int averageScore, int bestScore, String trend
    ) {}

    public record GoalTrackingResponse(
        String subject, String status, int progressPct,
        String message, int estimatedWeeksRemaining
    ) {}

    public record TeacherReportResponse(
        String learnerId, String subject, int averageScore,
        int totalSessions, boolean distressEverDetected,
        List<String> parentNotes, String teacherSummary
    ) {}
}
