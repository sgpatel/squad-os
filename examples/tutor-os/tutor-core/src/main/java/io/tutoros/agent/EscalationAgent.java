package io.tutoros.agent;

import io.squados.annotation.*;
import io.squados.remote.SquadClient;

/**
 * Escalation Agent — routes to a human teacher when the AI cannot help.
 *
 * Escalation triggers (any one is sufficient):
 *   1. Learner has failed the same concept 3 consecutive times
 *   2. GuardianAgent detected emotional distress signals
 *   3. ProgressAgent confirmed a 3-session plateau with no improvement
 *   4. Learner explicitly asks to speak to a teacher
 *   5. Session duration exceeds 90 minutes (burnout risk)
 *
 * What happens on escalation:
 *   1. EscalationAgent produces a teacher brief (context, history, recommendation)
 *   2. @AwaitApproval suspends the session and notifies the teacher portal
 *   3. Teacher reviews and chooses: join the session, send a message, or dismiss
 *   4. If teacher joins: session resumes with teacher in context
 *   5. If dismissed (24h timeout): session resumes with a new approach by DirectTutor
 *
 * Features:
 *   @AwaitApproval — blocks session until teacher responds (24h timeout)
 *   @RemoteSquad   — sends notification to the external TeacherPortal service
 *   @Traced        — escalation events are high-priority spans in OTel
 */
@Agent(
    role        = AgentRole.SUPPORT,
    name        = "EscalationAgent",
    description = "Pauses the session and notifies a human teacher when the learner " +
                  "is stuck, distressed, or plateaued. Produces a concise teacher brief " +
                  "with full session context and a recommended intervention."
)
@Traced(spanName = "escalation-human-handoff", traceOnError = true)
public class EscalationAgent {

    /**
     * Teacher portal connection — sends escalation notifications.
     * In production: HTTP POST to the school's notification service.
     *
     * Feature: @RemoteSquad
     */
    @RemoteSquad(url = "${tutor.teacher-portal.url}", auth = "api-key", timeoutMs = 15000)
    private SquadClient teacherPortalClient;

    /**
     * Generates the teacher brief — a concise summary of the situation
     * so the teacher can respond quickly and effectively.
     *
     * @param learnerName     student's name
     * @param learnerLevel    e.g. MIDDLE_SCHOOL
     * @param concept         the concept they're stuck on
     * @param sessionHistory  recent session summary (from ProgressAgent)
     * @param trigger         why escalation was triggered
     * @param distress        whether emotional distress was detected
     */
    public String teacherBriefPrompt(String learnerName, String learnerLevel,
                                      String concept, String sessionHistory,
                                      String trigger, boolean distress) {
        return """
            You are preparing a concise escalation brief for a teacher.
            A student needs human support. Write clearly and factually.

            Student: %s (%s level)
            Concept they're stuck on: %s
            Escalation trigger: %s
            Emotional distress detected: %b

            Recent session history:
            %s

            Write a teacher brief with:
            1. SITUATION (2 sentences): what the student is struggling with and why
            2. ATTEMPTS (bullet list): what the AI has already tried
            3. RECOMMENDATION (1 sentence): suggested teacher intervention
            4. URGENCY: LOW | NORMAL | HIGH (HIGH only if distress detected)

            Keep the total under 150 words. Be direct — teachers are busy.
            """.formatted(
                learnerName, learnerLevel, concept, trigger, distress, sessionHistory
            );
    }

    /**
     * Message shown to the student while the teacher is being notified.
     * Must be warm, reassuring, and non-alarming.
     */
    public String studentWaitMessage(String learnerName, boolean distress) {
        if (distress) {
            return """
                Hey %s, it sounds like you're having a tough time with this — and that's
                completely okay. Even the smartest people get stuck sometimes.

                I've asked your teacher to check in with you. They'll be with you soon.
                Take a break, get some water, and be kind to yourself. 💙

                I'll be right here when you're ready to continue.
                """.formatted(learnerName);
        }
        return """
            You've given this a real good effort, %s! This is a genuinely tricky concept.

            I've brought in your teacher for some extra support — they'll be able to
            explain it from a different angle that might click for you.

            While you wait, feel free to review your notes or take a short break. 👍
            """.formatted(learnerName);
    }

    /**
     * Message shown when the teacher dismisses (or times out) and
     * the session resumes with a fresh teaching approach.
     */
    public String resumeMessage(String learnerName) {
        return """
            Welcome back, %s! Your teacher has seen your progress and suggested
            we try a completely different approach for this concept.

            Let's start fresh — I'm going to explain it using a different analogy
            that might make it click. Ready?
            """.formatted(learnerName);
    }
}
