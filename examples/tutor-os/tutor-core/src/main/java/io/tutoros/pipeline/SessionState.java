package io.tutoros.pipeline;

import io.tutoros.model.*;

import java.time.Instant;
import java.util.*;

/**
 * Mutable session state threaded through the TutoringPipeline.
 *
 * Persisted to @DurableAgent store after every pipeline step so the
 * session can resume from the exact point after a restart or browser close.
 *
 * One SessionState exists per {learnerId + subject} pair.
 * Multiple sessions against the same state accumulate session history.
 */
public class SessionState {

    // ── Identity ─────────────────────────────────────────────────────
    private final String      sessionId;
    private final String      learnerId;
    private       LearnerProfile profile;

    // ── Plan & progress ───────────────────────────────────────────────
    private StudyPlan   currentPlan;
    private int         currentChapterIndex    = 0;
    private String      currentConcept         = "";
    private int         sessionCount           = 0;
    private boolean     firstSession           = true;

    // ── Mastery tracking ──────────────────────────────────────────────
    /** concept → mastery value 0.0–1.0 */
    private final Map<String, Double>   masteryMap          = new LinkedHashMap<>();
    /** concept → consecutive failure count */
    private final Map<String, Integer>  failureMap          = new HashMap<>();
    /** concept → recent mastery history (last 5 values) */
    private final Map<String, Deque<Double>> masteryHistory = new HashMap<>();

    // ── Session state ─────────────────────────────────────────────────
    private boolean distressDetected       = false;
    private int     consecutiveSessions    = 0;     // sessions since plan created
    private int     sessionDurationMinutes = 0;
    private Instant sessionStartTime;

    // ── Memory ────────────────────────────────────────────────────────
    private String  memoryContext      = "";        // injected by @AgentMemory each session
    private String  recentHistory      = "";        // last 3 session summaries as text
    private double  todoCompletionRate = 0.75;      // default; updated from memory

    // ── Visualisation queue ───────────────────────────────────────────
    private final Queue<String> pendingVisualisations = new LinkedList<>();

    // ── Constructor ───────────────────────────────────────────────────
    public SessionState(String learnerId, LearnerProfile profile) {
        this.learnerId    = learnerId;
        this.profile      = profile;
        this.sessionId    = "SESSION-" + learnerId + "-" + System.currentTimeMillis();
        this.sessionStartTime = Instant.now();
    }

    // ── Profile accessors ─────────────────────────────────────────────
    public LearnerProfileView profile() {
        return new LearnerProfileView(profile);
    }

    public void updateProfile(LearnerProfile updated) {
        this.profile   = updated;
        this.firstSession = false;
    }

    // ── Plan accessors ────────────────────────────────────────────────
    public boolean isFirstSession()  { return firstSession; }
    public boolean needsNewPlan()    { return currentPlan == null || isChapterComplete(); }

    public void updatePlan(StudyPlan plan) {
        this.currentPlan        = plan;
        this.currentChapterIndex = plan.currentChapterIndex;
        advanceToCurrentChapterConcept();
    }

    public String currentChapterTitle() {
        if (currentPlan == null) return "Introduction";
        String[] chapters = currentPlan.chapters.split("\n");
        return currentChapterIndex < chapters.length
               ? chapters[currentChapterIndex] : "Final Review";
    }

    public boolean isChapterComplete() {
        return currentMasteryPct() >= 85;
    }

    public void advanceChapter() {
        if (currentPlan != null) currentChapterIndex++;
        advanceToCurrentChapterConcept();
    }

    private void advanceToCurrentChapterConcept() {
        // Derive current concept from chapter title (first noun phrase after colon)
        String chapter = currentChapterTitle();
        int colon = chapter.indexOf(':');
        currentConcept = colon >= 0 ? chapter.substring(colon + 1).strip() : chapter;
    }

    // ── Mastery accessors ─────────────────────────────────────────────
    public double currentMastery(String concept) {
        return masteryMap.getOrDefault(concept, 0.0);
    }

    public int currentMasteryPct() {
        return (int) (currentMastery(currentConcept) * 100);
    }

    public void updateMastery(String concept, double newValue) {
        double clamped = Math.min(1.0, Math.max(0.0, newValue));
        masteryMap.put(concept, clamped);
        masteryHistory.computeIfAbsent(concept, k -> new ArrayDeque<>()).addLast(clamped);
        if (masteryHistory.get(concept).size() > 5)
            masteryHistory.get(concept).pollFirst();
    }

    public double[] masteryHistoryFor(String concept) {
        Deque<Double> h = masteryHistory.getOrDefault(concept, new ArrayDeque<>());
        return h.stream().mapToDouble(Double::doubleValue).toArray();
    }

    public double overallMastery() {
        if (masteryMap.isEmpty()) return 0.0;
        return masteryMap.values().stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    // ── Failure tracking ──────────────────────────────────────────────
    public int consecutiveFailures(String concept) {
        return failureMap.getOrDefault(concept, 0);
    }

    public void recordFailure(String concept) {
        failureMap.merge(concept, 1, Integer::sum);
    }

    public void resetFailures(String concept) {
        failureMap.remove(concept);
    }

    // ── Session metadata ──────────────────────────────────────────────
    public String sessionId()           { return sessionId; }
    public String learnerId()           { return learnerId; }
    public String currentConcept()      { return currentConcept; }
    public int    sessionCount()        { return sessionCount; }
    public void   incrementSession()    { sessionCount++; consecutiveSessions++; firstSession = false; }
    public boolean distressDetected()   { return distressDetected; }
    public void setDistressDetected(boolean v) { this.distressDetected = v; }
    public String memoryContext()       { return memoryContext; }
    public void setMemoryContext(String v)     { this.memoryContext = v; }
    public String recentHistory()       { return recentHistory; }
    public void setRecentHistory(String v)     { this.recentHistory = v; }
    public double todoCompletionRate()  { return todoCompletionRate; }
    public void setTodoCompletionRate(double v){ this.todoCompletionRate = v; }

    public int sessionDurationMinutes() {
        return (int) java.time.Duration.between(sessionStartTime, Instant.now()).toMinutes();
    }

    // ── Visualisation queue ───────────────────────────────────────────
    public void queueVisualisation(String renderRequest) {
        pendingVisualisations.add(renderRequest);
    }

    public String pollVisualisation() {
        return pendingVisualisations.poll();
    }

    public boolean hasPendingVisualisations() {
        return !pendingVisualisations.isEmpty();
    }

    // ── Snapshot for @DurableAgent checkpoint ────────────────────────
    /**
     * Produces a compact snapshot string stored in the durable checkpoint.
     * Used to reconstruct SessionState after a restart.
     */
    public String toCheckpoint() {
        return String.format(
            "{\"sessionId\":\"%s\",\"learnerId\":\"%s\",\"sessionCount\":%d," +
            "\"chapterIndex\":%d,\"concept\":\"%s\",\"overallMastery\":%.2f}",
            sessionId, learnerId, sessionCount,
            currentChapterIndex, currentConcept, overallMastery()
        );
    }

    /**
     * Lightweight view over LearnerProfile exposing typed accessors.
     * Avoids leaking the mutable profile fields outside the package.
     */
    public record LearnerProfileView(LearnerProfile raw) {
        public String name()                   { return raw.name; }
        public String level()                  { return raw.level; }
        public String profession()             { return raw.profession; }
        public String subject()                { return raw.subject; }
        public String topic()                  { return raw.topic; }
        public String goal()                   { return raw.goal; }
        public String learningStyle()          { return raw.learningStyle; }
        public String bloomsLevel()            { return raw.bloomsLevel; }
        public String analogyDomain()          { return raw.analogyDomain; }
        public String preferredTeachingStyle() { return raw.preferredTeachingStyle; }
        public int    sessionsPerWeek()        { return raw.sessionsPerWeek; }
        public int    avgSessionMinutes()      { return raw.avgSessionMinutes; }
        public String gapConcepts()            { return raw.gapConcepts; }
        public String masteredConcepts()       { return raw.masteredConcepts; }
        public boolean atRisk()                { return raw.atRisk; }
        @Override public String toString()     {
            return String.format("LearnerProfile{name=%s,level=%s,goal=%s,style=%s}",
                raw.name, raw.level, raw.goal, raw.learningStyle);
        }
    }
}
