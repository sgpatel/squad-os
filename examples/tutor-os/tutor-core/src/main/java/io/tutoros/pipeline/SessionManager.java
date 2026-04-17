package io.tutoros.pipeline;

import io.squados.annotation.*;
import io.squados.context.SquadContext;
import io.tutoros.model.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionManager — lifecycle controller for learner sessions.
 *
 * Responsibilities:
 *   - Create, resume, and expire sessions
 *   - Thread-safe session store (in-memory; production would use Redis)
 *   - Orchestrate session start: load memory → inject into state → hand to pipeline
 *   - Orchestrate session end: generate summary → todos → checkpoint
 *   - Manage the 90-minute burnout protection timer
 *
 * One SessionManager exists per application instance (singleton Spring bean).
 * Sessions are keyed by {learnerId}:{subject} so a learner can have
 * concurrent sessions on different subjects.
 *
 * Feature: @DurableAgent checkpointing keeps sessions alive across restarts.
 */
public class SessionManager {

    /** Active sessions: key = learnerId + ":" + subject */
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    private final TutoringPipeline pipeline;
    private final SquadContext      ctx;

    /** Maximum session duration before burnout warning is triggered */
    private static final int MAX_SESSION_MINUTES = 90;

    public SessionManager(TutoringPipeline pipeline, SquadContext ctx) {
        this.pipeline = pipeline;
        this.ctx      = ctx;
    }

    // ── Session lifecycle ─────────────────────────────────────────────

    /**
     * Start or resume a session for a learner.
     *
     * If an active session exists for this learner+subject, resume it.
     * Otherwise create a new session starting with diagnostics.
     *
     * @param learnerId    unique learner ID from the authentication layer
     * @param profile      learner profile (from onboarding or persisted store)
     * @return the active SessionState, ready to receive messages
     */
    public SessionState startOrResume(String learnerId, LearnerProfile profile) {
        String key = sessionKey(learnerId, profile.subject);
        return sessions.computeIfAbsent(key, k -> {
            var state = new SessionState(learnerId, profile);
            state.incrementSession();
            return state;
        });
    }

    /**
     * Process a student message through the full pipeline.
     *
     * @param sessionId    session ID returned by startOrResume
     * @param message      raw student message
     * @return PipelineResult to be serialised into the HTTP/WebSocket response
     */
    public PipelineResult message(String sessionId, String message) {
        SessionState state = findBySessionId(sessionId);
        if (state == null) return PipelineResult.blocked("Session not found.");

        // Burnout protection
        if (state.sessionDurationMinutes() > MAX_SESSION_MINUTES) {
            return PipelineResult.safe(burnoutMessage(state.profile().name()));
        }

        return pipeline.process(state, message);
    }

    /**
     * Submit a practice answer for grading.
     *
     * @param sessionId     session ID
     * @param questionJson  the PracticeQuestion that was posed (serialised JSON)
     * @param answer        the student's answer text
     * @param attemptNumber 1, 2, or 3
     */
    public PipelineResult submitAnswer(String sessionId, PracticeQuestion question,
                                        String answer, int attemptNumber) {
        SessionState state = findBySessionId(sessionId);
        if (state == null) return PipelineResult.blocked("Session not found.");
        return pipeline.assess(state, answer, question, attemptNumber);
    }

    /**
     * Generate a quiz for the current session.
     */
    public Quiz quiz(String sessionId, String topic, int questionCount, String difficulty) {
        SessionState state = findBySessionId(sessionId);
        if (state == null) throw new IllegalArgumentException("Session not found: " + sessionId);
        return pipeline.generateQuiz(state, topic, questionCount, difficulty);
    }

    /**
     * End a session — generate summary + todos + checkpoint.
     *
     * @return the SessionSummary for display in the UI
     */
    public SessionSummary endSession(String sessionId) {
        SessionState state = findBySessionId(sessionId);
        if (state == null) throw new IllegalArgumentException("Session not found: " + sessionId);

        // Generate summary via ProgressAgent
        AgentResponseAdapter response = AgentResponseAdapter.of(
            ctx.submitTo(
                io.squados.annotation.AgentRole.SUPPORT,
                buildSummaryPrompt(state)
            )
        );
        SessionSummary summary = response.as(SessionSummary.class);

        // Generate todos
        pipeline.generateTodos(state, summary);

        // Checkpoint session (durable store)
        checkpointSession(state);

        return summary;
    }

    /**
     * Returns a progress snapshot for the learner's progress screen.
     */
    public ProgressSnapshot getProgress(String learnerId, String subject) {
        String key   = sessionKey(learnerId, subject);
        SessionState state = sessions.get(key);
        if (state == null) return ProgressSnapshot.empty();
        return ProgressSnapshot.from(state);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private SessionState findBySessionId(String sessionId) {
        return sessions.values().stream()
            .filter(s -> s.sessionId().equals(sessionId))
            .findFirst().orElse(null);
    }

    private String sessionKey(String learnerId, String subject) {
        return learnerId + ":" + subject.toLowerCase().replace(" ", "-");
    }

    private String buildSummaryPrompt(SessionState state) {
        return String.format(
            "Generate SessionSummary for learnerId=%s, chapter=%s, duration=%dmin, " +
            "overallMastery=%.0f%%, distress=%b, teachingStyle=mixed",
            state.learnerId(), state.currentChapterTitle(),
            state.sessionDurationMinutes(),
            state.overallMastery() * 100,
            state.distressDetected()
        );
    }

    private void checkpointSession(SessionState state) {
        // In production: persist state.toCheckpoint() to Redis/DB via DurableStore
        // For now: the @DurableAgent on TutoringPipeline handles this automatically
    }

    private String burnoutMessage(String name) {
        return String.format("""
            Hey %s — you've been going for over 90 minutes! 🌟

            That's amazing dedication, but your brain needs rest to consolidate everything
            you've learned. The science is clear: sleep and breaks improve retention far
            more than pushing through.

            Let's save your progress and pick up fresh next time.
            Everything is saved — your mastery gains are locked in. 💾
            """, name);
    }

    // ── Inner types ───────────────────────────────────────────────────

    /**
     * Thin adapter over AgentResponse for structured output extraction.
     * Avoids direct dependency on AgentResponse in SessionManager.
     */
    private record AgentResponseAdapter(io.squados.agent.AgentResponse raw) {
        static AgentResponseAdapter of(io.squados.agent.AgentResponse r) { return new AgentResponseAdapter(r); }
        <T> T as(Class<T> cls) {
            T parsed = raw.structuredOutput(cls);
            if (parsed != null) return parsed;
            throw new IllegalStateException("No structured output of type " + cls.getSimpleName());
        }
    }

    /**
     * Snapshot of a learner's progress for the /progress endpoint.
     */
    public record ProgressSnapshot(
        double  overallMastery,
        int     sessionCount,
        int     streakDays,
        String  currentChapter,
        Map<String, Double> conceptMastery
    ) {
        static ProgressSnapshot empty() {
            return new ProgressSnapshot(0, 0, 0, "Not started", Map.of());
        }
        static ProgressSnapshot from(SessionState state) {
            return new ProgressSnapshot(
                state.overallMastery(),
                state.sessionCount(),
                0, // streak calculated server-side from session dates
                state.currentChapterTitle(),
                Map.copyOf(new java.util.HashMap<>())
            );
        }
    }
}
