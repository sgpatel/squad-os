package io.tutoros.agent;

import io.squados.annotation.*;

/**
 * Socratic Tutor Agent — Debate Participant A.
 *
 * Teaches through guided discovery: asks questions that lead the learner
 * to construct understanding themselves rather than receiving it passively.
 *
 * Wins the @Debate when:
 *   - The learner's profile shows SOCRATIC preferred style
 *   - The concept is one the learner is "almost there" on (mastery 50–80%)
 *   - The learner has shown engagement with questions in previous sessions
 *   - The learning goal is DEEP_UNDERSTANDING or CURIOSITY
 *
 * Loses the @Debate when:
 *   - Learner level is PRIMARY (too young for Socratic frustration)
 *   - Mastery is below 30% (learner needs foundation first)
 *   - Goal is QUICK_REVISION (no time for discovery)
 *   - Learner showed distress in the last 2 sessions
 *
 * Features:
 *   @Debate participant — scored by DebateEngine against DirectTutorAgent
 *   @AgentMemory       — recalls which Socratic questions worked before
 *   @OptimizePrompt    — prompt is improved based on "I understand" signals
 */
@Agent(
    // Distinct role from AssessmentAgent (CRITIC) — both teachers and graders
    // previously claimed CRITIC which caused the registry to silently evict
    // whichever registered first, routing teaching prompts to the grader and
    // failing JSON parsing for AssessmentFeedback. EDITOR is the closest
    // behavioural match (low temp, refinement-focused) and is unused elsewhere.
    role        = AgentRole.EDITOR,
    name        = "SocraticTutorAgent",
    description = "Teaches through guided questions. Leads the learner to construct " +
                  "understanding themselves via the Socratic method. " +
                  "Debate participant: wins when discovery learning is appropriate."
)
@AgentMemory(topK = 3, minScore = 0.70f, scope = "squad")
@OptimizePrompt(scoreThreshold = 0.82f, maxIterations = 4)
@Traced(spanName = "socratic-tutor")
public class SocraticTutorAgent {

    /**
     * Main tutoring prompt — question-led style.
     *
     * @param studentQuestion  what the student asked
     * @param concept          the concept being taught
     * @param learnerLevel     e.g. "MIDDLE_SCHOOL"
     * @param bloomsLevel      current Bloom's level of the learner
     * @param masteryPct       0–100 current mastery for this concept
     * @param contentContext   grounded material from ContentAgent
     * @param memoryContext    recalled past session snippets from @AgentMemory
     */
    public String teachingPrompt(String studentQuestion, String concept,
                                  String learnerLevel, String bloomsLevel,
                                  int masteryPct, String contentContext,
                                  String memoryContext) {
        return """
            You are a Socratic tutor. You NEVER explain directly.
            You only ask questions that guide the learner to discover the answer.

            Learner: %s level | Current mastery of '%s': %d%% | Bloom's: %s

            Context from sources:
            %s

            Past session memory:
            %s

            Student asked: "%s"

            Rules:
            1. Start with a question that connects to something the learner already knows
            2. Each question should be ONE step closer to the answer — never jump ahead
            3. If they answer correctly, affirm briefly and ask the next guiding question
            4. If they answer incorrectly, don't say "wrong" — ask a simpler sub-question
            5. Maximum 2 questions per response — don't overwhelm
            6. If after 3 exchanges they are still stuck, give a hint (not the answer)
            7. End only when they have verbalised the concept in their own words

            Begin your Socratic sequence now:
            """.formatted(learnerLevel, concept, masteryPct, bloomsLevel,
                          contentContext, memoryContext, studentQuestion);
    }

    /**
     * Debate scoring prompt.
     *
     * The DebateEngine calls this to get SocraticTutor's argument for
     * why Socratic teaching is right for THIS learner on THIS concept.
     */
    public String debateArgument(String learnerProfile, String concept) {
        return """
            You are arguing for Socratic teaching for this learner on concept: %s.

            Learner profile:
            %s

            Provide a 3-point argument for why guided questioning is the right
            approach for this specific learner right now. Be concise — 1 sentence per point.

            Score your own confidence 0.0–1.0 at the end.
            Format: POINT1|POINT2|POINT3|CONFIDENCE:0.XX
            """.formatted(concept, learnerProfile);
    }
}
