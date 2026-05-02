package io.tutoros.api;

import io.squados.agent.AgentResponse;
import io.squados.annotation.AgentRole;
import io.squados.context.SquadContext;
import io.tutoros.agent.SyllabusSuggesterAgent;
import io.tutoros.model.Syllabus;
import io.tutoros.pipeline.SessionManager;
import io.tutoros.pipeline.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Syllabus Controller — lets the learner either
 *   1. ask the tutor to <b>suggest</b> a syllabus for the active topic, or
 *   2. <b>save</b> their own (pasted) syllabus,
 * and have the planner respect it on the next turn.
 *
 * Tied to PR-A's subject/topic segregation: a custom syllabus is the
 * authoritative scope for the active session, so subsequent
 * CurriculumPlannerAgent calls produce StudyPlans that stay strictly
 * inside what the learner actually has to cover.
 *
 * Endpoints:
 *   POST /api/syllabus/suggest  — generate a syllabus via SyllabusSuggesterAgent
 *   POST /api/syllabus/save     — persist a (suggested or pasted) syllabus
 *                                 onto the active session's LearnerProfile
 *   GET  /api/syllabus/{sid}    — read back what's currently saved
 *
 * Storage is per-session (lives on the in-memory SessionState today).
 * When TutorOS gains a persistent profile store, the same wire shape will
 * back onto that — no API change needed.
 */
@RestController
@RequestMapping("/api/syllabus")
@CrossOrigin(origins = "*")
public class SyllabusController {

    private static final Logger log = LoggerFactory.getLogger(SyllabusController.class);

    private final SquadContext            ctx;
    private final SyllabusSuggesterAgent  suggester;
    private final SessionManager          sessions;

    public SyllabusController(SquadContext ctx,
                               SyllabusSuggesterAgent suggester,
                               SessionManager sessions) {
        this.ctx       = ctx;
        this.suggester = suggester;
        this.sessions  = sessions;
    }

    // ── DTOs ─────────────────────────────────────────────────────────

    /**
     * Request body for {@code POST /suggest}.
     *
     * Either {@code sessionId} OR all three explicit fields must be present:
     *   - sessionId  → resolves subject/topic/level from the active session
     *   - subject + topic + level → standalone (e.g. pre-session preview)
     *
     * When sessionId is given, explicit fields override the session's
     * values for that single call (lets the UI preview alternatives
     * without mutating the session).
     */
    public record SuggestRequest(
        String sessionId,
        String subject,
        String topic,
        String level
    ) {}

    /**
     * Request body for {@code POST /save}. The {@code syllabus} can be:
     *   - a Syllabus object freshly returned from /suggest, or
     *   - a learner-pasted Syllabus (typically only chapters + topic
     *     populated; the controller fills sane defaults for the rest).
     */
    public record SaveRequest(
        String   sessionId,
        Syllabus syllabus
    ) {}

    // ── /suggest ─────────────────────────────────────────────────────

    /**
     * POST /api/syllabus/suggest
     *
     * Builds a level-appropriate syllabus for the (subject, topic) triple.
     * Returns 400 if neither sessionId nor a complete subject+topic+level
     * triple is supplied.
     *
     * The agent emits structured JSON (Syllabus.class). On parse failure
     * we surface the raw text inside a stub Syllabus so the UI can still
     * show something useful and the learner can edit it.
     */
    @PostMapping("/suggest")
    public ResponseEntity<Syllabus> suggest(@RequestBody SuggestRequest body) {
        ResolvedScope scope = resolveScope(body);
        if (scope == null) {
            return ResponseEntity.badRequest().build();
        }

        String prompt = suggester.suggestPrompt(scope.subject, scope.topic, scope.level);
        AgentResponse resp;
        try {
            resp = ctx.submitTo(AgentRole.WILDCARD, prompt);
        } catch (RuntimeException e) {
            log.warn("syllabus suggester failed", e);
            return ResponseEntity.status(502).build();
        }

        Syllabus s = resp != null ? resp.structuredOutput(Syllabus.class) : null;
        if (s == null) {
            // Fallback: keep the raw text so the UI can render + edit.
            s = new Syllabus();
            s.subject   = scope.subject;
            s.topic     = scope.topic;
            s.level     = scope.level;
            s.chapters  = resp != null ? resp.content() : "";
            s.rationale = "(LLM response did not match Syllabus schema; raw text preserved)";
            s.source    = "SUGGESTED";
        } else {
            // Defensive normalisation — make sure source/level/subject/topic
            // are non-null even if the LLM dropped them.
            if (s.source  == null || s.source.isBlank())  s.source  = "SUGGESTED";
            if (s.subject == null || s.subject.isBlank()) s.subject = scope.subject;
            if (s.topic   == null || s.topic.isBlank())   s.topic   = scope.topic;
            if (s.level   == null || s.level.isBlank())   s.level   = scope.level;
        }
        return ResponseEntity.ok(s);
    }

    // ── /save ────────────────────────────────────────────────────────

    /**
     * POST /api/syllabus/save
     *
     * Writes the syllabus onto the session's LearnerProfile so the
     * CurriculumPlannerAgent picks it up on the next planning step.
     * Returns 404 when the session id is unknown, 400 when the body is
     * incomplete.
     */
    @PostMapping("/save")
    public ResponseEntity<Syllabus> save(@RequestBody SaveRequest body) {
        if (body == null || body.sessionId == null || body.syllabus == null
            || body.syllabus.chapters == null || body.syllabus.chapters.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        SessionState state = sessions.getSession(body.sessionId);
        if (state == null) return ResponseEntity.notFound().build();

        Syllabus s = body.syllabus;
        // If the learner pasted their own and didn't tag it, mark it CUSTOM
        // — distinguishes "the LLM proposed this" from "the learner owns this".
        if (s.source == null || s.source.isBlank()) s.source = "CUSTOM";
        // Echo the active subject/topic when the body left them blank — keeps
        // PR-A's segregation tags consistent across writes.
        if (s.subject == null || s.subject.isBlank()) s.subject = state.profile().subject();
        if (s.topic   == null || s.topic.isBlank())   s.topic   = state.profile().topic();
        if (s.level   == null || s.level.isBlank())   s.level   = state.profile().level();

        state.profile().raw().customSyllabus = s;
        log.info("Saved {} syllabus for session={} subject={} topic={} ({} chars)",
            s.source, body.sessionId, s.subject, s.topic,
            s.chapters != null ? s.chapters.length() : 0);
        return ResponseEntity.ok(s);
    }

    // ── /{sid} ────────────────────────────────────────────────────────

    /**
     * GET /api/syllabus/{sessionId} — read back the saved syllabus.
     * Returns 404 when no session, 204 when the session has no syllabus saved yet.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<Syllabus> get(@PathVariable String sessionId) {
        SessionState state = sessions.getSession(sessionId);
        if (state == null) return ResponseEntity.notFound().build();
        Syllabus s = state.profile().customSyllabus();
        return s == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(s);
    }

    // ── helpers ───────────────────────────────────────────────────────

    private record ResolvedScope(String subject, String topic, String level) {}

    /**
     * Resolve subject/topic/level from the request body and (optionally)
     * the active session. Explicit fields on the body take precedence
     * over session-derived ones.
     */
    private ResolvedScope resolveScope(SuggestRequest body) {
        String subject = body != null ? body.subject : null;
        String topic   = body != null ? body.topic   : null;
        String level   = body != null ? body.level   : null;

        if (body != null && body.sessionId != null) {
            SessionState state = sessions.getSession(body.sessionId);
            if (state != null) {
                if (subject == null || subject.isBlank()) subject = state.profile().subject();
                if (topic   == null || topic.isBlank())   topic   = state.profile().topic();
                if (level   == null || level.isBlank())   level   = state.profile().level();
            }
        }

        if (subject == null || subject.isBlank() ||
            topic   == null || topic.isBlank()   ||
            level   == null || level.isBlank()) {
            return null;
        }
        return new ResolvedScope(subject.trim(), topic.trim(), level.trim());
    }
}
