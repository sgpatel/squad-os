package io.tutoros.api;

import io.tutoros.model.*;
import io.tutoros.pipeline.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Session Controller — primary REST API for the TutorOS tutoring session.
 *
 * All endpoints require a learnerId header (set by the auth layer / JWT).
 * WebSocket streaming is handled by TutorWebSocketHandler.
 *
 * Endpoints:
 *   POST /session/start              — onboard learner, create/resume session
 *   POST /session/{id}/message       — send a message, get tutor response
 *   POST /session/{id}/answer        — submit a practice answer, get feedback
 *   GET  /session/{id}/question      — get the current practice question
 *   POST /session/{id}/end           — end session, generate summary + todos
 *   GET  /session/{id}/summary       — retrieve the latest session summary
 *   GET  /session/{id}/todos         — retrieve generated todos
 */
@RestController
@RequestMapping("/session")
@CrossOrigin(origins = "*") // tighten in production
public class SessionController {

    private final SessionManager sessionManager;

    public SessionController(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    // ── Start / resume ────────────────────────────────────────────────

    /**
     * POST /session/start
     *
     * Body: StartSessionRequest (learner profile from onboarding)
     * Response: StartSessionResponse (sessionId, profile, first message)
     *
     * If a session already exists for this learnerId+subject it is resumed.
     * Otherwise a new session begins with the DiagnosticAgent.
     */
    @PostMapping("/start")
    public ResponseEntity<StartSessionResponse> startSession(
            @RequestBody StartSessionRequest request) {

        LearnerProfile profile = buildProfile(request);
        SessionState state = sessionManager.startOrResume(request.learnerId(), profile);

        // First message — either diagnostic intro or resume message
        String firstMessage = state.isFirstSession()
            ? "Hi " + profile.name + "! I'm TutorOS — your personal AI tutor. " +
              "Before we dive in, I'll ask you a few quick questions to understand where you are. Ready?"
            : "Welcome back, " + profile.name + "! We left off at **" +
              state.currentChapterTitle() + "**. Shall we continue?";

        return ResponseEntity.ok(new StartSessionResponse(
            state.sessionId(),
            profile,
            firstMessage,
            state.currentChapterTitle(),
            (int) Math.round(state.overallMastery() * 100)
        ));
    }

    // ── Send message ──────────────────────────────────────────────────

    /**
     * POST /session/{id}/message
     *
     * Body: MessageRequest { message }
     * Response: MessageResponse (tutor text, teachingStyle, visualQueued, etc.)
     *
     * The full 12-step pipeline runs here.
     * For streaming: use WebSocket on /ws/session/{id} instead.
     */
    @PostMapping("/{sessionId}/message")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable String sessionId,
            @RequestBody MessageRequest request) {

        PipelineResult result = sessionManager.message(sessionId, request.message());

        return ResponseEntity.ok(switch (result) {
            case PipelineResult.TutorResponse r ->
                new MessageResponse("TUTOR", r.text(), r.teachingStyle(), r.visualQueued(), null);
            case PipelineResult.SafeRefusal r ->
                new MessageResponse("SAFE_REFUSAL", r.text(), null, false, null);
            case PipelineResult.BlockedResult r ->
                new MessageResponse("BLOCKED", "I can't help with that.", null, false, null);
            case PipelineResult.DiagnosticResult r ->
                new MessageResponse("DIAGNOSTIC", r.introMessage(), null, false, r.profile());
            case PipelineResult.PlanResult r ->
                new MessageResponse("PLAN", r.introMessage(), null, false, r.plan());
            case PipelineResult.EscalationResult r ->
                new MessageResponse("ESCALATION", r.studentMessage(), null, false, null);
            case PipelineResult.FeedbackResult r ->
                new MessageResponse("FEEDBACK", null, null, false, r.feedback());
        });
    }

    // ── Practice answer ───────────────────────────────────────────────

    /**
     * POST /session/{id}/answer
     *
     * Body: AnswerRequest { questionJson, answer, attemptNumber }
     * Response: FeedbackResponse (AssessmentFeedback + mastery update)
     */
    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<FeedbackResponse> submitAnswer(
            @PathVariable String sessionId,
            @RequestBody AnswerRequest request) {

        PipelineResult result = sessionManager.submitAnswer(
            sessionId,
            request.question(),
            request.answer(),
            request.attemptNumber()
        );

        if (result instanceof PipelineResult.FeedbackResult fr) {
            return ResponseEntity.ok(new FeedbackResponse(fr.feedback()));
        }
        return ResponseEntity.internalServerError().build();
    }

    // ── Session end ───────────────────────────────────────────────────

    /**
     * POST /session/{id}/end
     *
     * Triggers: summary generation → todo generation → durable checkpoint.
     * Response: SessionSummary
     */
    @PostMapping("/{sessionId}/end")
    public ResponseEntity<SessionSummary> endSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionManager.endSession(sessionId));
    }

    // ── Quick quiz ────────────────────────────────────────────────────

    /**
     * POST /session/{id}/quiz
     *
     * Body: QuizRequest { topic, questionCount, difficulty }
     * Response: Quiz
     */
    @PostMapping("/{sessionId}/quiz")
    public ResponseEntity<Quiz> generateQuiz(
            @PathVariable String sessionId,
            @RequestBody QuizRequest request) {

        Quiz quiz = sessionManager.quiz(
            sessionId,
            request.topic(),
            request.questionCount() > 0 ? request.questionCount() : 5,
            request.difficulty()
        );
        return ResponseEntity.ok(quiz);
    }

    // ── Helper ────────────────────────────────────────────────────────

    private LearnerProfile buildProfile(StartSessionRequest req) {
        LearnerProfile p  = new LearnerProfile();
        p.name            = req.name();
        p.level           = req.level();
        p.profession      = req.profession() != null ? req.profession() : "";
        p.subject         = req.subjects() != null && !req.subjects().isEmpty()
                            ? req.subjects().get(0) : "General";
        p.topic           = req.topic() != null ? req.topic() : p.subject;
        p.goal            = req.goal();
        p.learningStyle   = "DIRECT";    // default; DiagnosticAgent will refine
        p.bloomsLevel     = "REMEMBER";  // default; DiagnosticAgent will refine
        p.analogyDomain   = req.analogyDomain() != null ? req.analogyDomain() : "everyday life";
        p.preferredTeachingStyle = "DIRECT";
        p.sessionsPerWeek = req.sessionsPerWeek() > 0 ? req.sessionsPerWeek() : 3;
        p.avgSessionMinutes = 40;
        return p;
    }

    // ── Request / Response records ────────────────────────────────────

    public record StartSessionRequest(
        String learnerId, String name, String level, String profession,
        java.util.List<String> subjects, String topic, String goal,
        String analogyDomain, int sessionsPerWeek
    ) {}

    public record StartSessionResponse(
        String sessionId, LearnerProfile profile,
        String firstMessage, String currentChapter, int masteryPct
    ) {}

    public record MessageRequest(String message) {}

    public record MessageResponse(
        String type, String text, String teachingStyle,
        boolean visualQueued, Object payload
    ) {}

    public record AnswerRequest(
        PracticeQuestion question, String answer, int attemptNumber
    ) {}

    public record FeedbackResponse(AssessmentFeedback feedback) {}

    public record QuizRequest(String topic, int questionCount, String difficulty) {}
}
