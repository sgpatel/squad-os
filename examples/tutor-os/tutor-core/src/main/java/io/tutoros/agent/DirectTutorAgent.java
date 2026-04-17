package io.squados.examples.tutoros.agent;

import io.squados.annotation.*;
import io.squados.llm.StdoutTokenWriter;

/**
 * Direct Tutor Agent — Debate Participant B.
 *
 * Teaches through clear, structured, direct explanation. Adapts language
 * complexity and example type to the learner's level and analogyDomain.
 *
 * Wins the @Debate when:
 *   - Learner is PRIMARY level (needs explicit instruction)
 *   - Concept mastery is below 30% (needs a solid foundation first)
 *   - Goal is QUICK_REVISION or PASS_EXAMS (efficiency over discovery)
 *   - Learner showed distress recently (needs certainty and clarity)
 *   - VISUAL learning style (needs diagrams + descriptions)
 *
 * Explanation format adapts to level:
 *   PRIMARY       → simple words, emojis, concrete real-world examples
 *   MIDDLE_SCHOOL → step-by-step with sub-headings, one analogy
 *   SENIOR_SCHOOL → technical terms, links to exam mark schemes
 *   UNIVERSITY    → academic rigour, citations, methodology discussion
 *   PROFESSIONAL  → domain-specific examples, practical applications
 *   EDUCATOR      → pedagogical framing, teaching tips, common misconceptions
 *
 * Features:
 *   @Debate         — participant against SocraticTutorAgent
 *   @Streaming      — explanation streams token-by-token to the UI
 *   @AgentMemory    — recalls which analogies and explanations worked
 *   @OptimizePrompt — prompt improved based on mastery gain signals
 *   @Benchmark      — explanation quality measured against golden examples
 */
@Agent(
    role        = AgentRole.WRITER,
    name        = "DirectTutorAgent",
    description = "Teaches through clear, structured, level-adapted explanation. " +
                  "Streams responses in real time. " +
                  "Debate participant: wins when direct instruction is optimal."
)
@Streaming(writer = StdoutTokenWriter.class, chunkSize = 3)
@AgentMemory(topK = 3, minScore = 0.72f, scope = "squad")
@OptimizePrompt(scoreThreshold = 0.85f, maxIterations = 5)
@Benchmark(
    dataset  = "classpath:benchmarks/tutor-explanation-golden.json",
    minScore = 0.80f
)
@Traced(spanName = "direct-tutor")
public class DirectTutorAgent {

    /**
     * Main explanation prompt — style adapted to learner level and goal.
     */
    public String teachingPrompt(String studentQuestion, String concept,
                                  String level, String goal, String bloomsLevel,
                                  String learningStyle, String analogyDomain,
                                  String profession, String contentContext,
                                  String memoryContext) {

        String styleGuide = buildStyleGuide(level, goal, learningStyle, analogyDomain, profession);

        return """
            You are an expert tutor. You explain clearly, concisely, and engagingly.

            Student question: "%s"
            Concept: %s
            Learner: %s level | Goal: %s | Bloom's target: one level above %s

            Style guide for this learner:
            %s

            Grounded source material (use this, don't hallucinate):
            %s

            Memory from past sessions (build on this):
            %s

            Structure your response:
            1. One-sentence hook that connects to what they already know
            2. Core explanation — clear, accurate, levelled
            3. Real-world analogy (from %s domain if possible)
            4. One concrete example or worked step
            5. A check-in question: "Does that make sense? Try to explain back to me: ___"

            Keep the total under 250 words. Use **bold** for key terms.
            """.formatted(
                studentQuestion, concept, level, goal, bloomsLevel,
                styleGuide, contentContext, memoryContext, analogyDomain
            );
    }

    /**
     * Debate scoring prompt.
     *
     * DebateEngine calls this to get DirectTutor's argument for
     * why direct instruction is right for THIS learner on THIS concept.
     */
    public String debateArgument(String learnerProfile, String concept) {
        return """
            You are arguing for direct (explicit) instruction for this learner on concept: %s.

            Learner profile:
            %s

            Provide a 3-point argument for why clear direct explanation is the right
            approach for this specific learner right now. 1 sentence per point.

            Score your own confidence 0.0–1.0 at the end.
            Format: POINT1|POINT2|POINT3|CONFIDENCE:0.XX
            """.formatted(concept, learnerProfile);
    }

    // ── Style adaptation ─────────────────────────────────────────────
    private String buildStyleGuide(String level, String goal,
                                   String learningStyle, String analogyDomain,
                                   String profession) {
        StringBuilder sb = new StringBuilder();

        sb.append(switch (level) {
            case "PRIMARY"       -> "- Use very simple words (age 5–11). Short sentences. Use emojis to engage.\n" +
                                    "- No jargon. Every technical word must be immediately explained.\n" +
                                    "- Use story-based framing: 'Imagine a tiny factory inside the leaf...'\n";
            case "MIDDLE_SCHOOL" -> "- Clear but accurate language (age 11–16). Introduce technical terms with definitions.\n" +
                                    "- Use sub-headings and numbered steps for processes.\n" +
                                    "- One relatable analogy per concept.\n";
            case "SENIOR_SCHOOL" -> "- Full technical vocabulary — this is exam prep.\n" +
                                    "- Reference mark scheme language where relevant.\n" +
                                    "- Flag common exam mistakes after the explanation.\n";
            case "UNIVERSITY"    -> "- Academic rigour. Reference theories and primary literature.\n" +
                                    "- Discuss methodology and limitations where relevant.\n" +
                                    "- Encourage critical evaluation, not just recall.\n";
            case "EDUCATOR"      -> "- Frame explanations in terms of pedagogy.\n" +
                                    "- Include common student misconceptions and how to address them.\n" +
                                    "- Suggest teaching strategies alongside the content.\n";
            case "PROFESSIONAL"  -> "- Use domain-specific examples from " + profession + ".\n" +
                                    "- Focus on practical application over theoretical depth.\n" +
                                    "- Connect to real tools, systems, or decisions they face.\n";
            default              -> "- Standard clear explanation.\n";
        });

        sb.append(switch (learningStyle) {
            case "VISUAL"      -> "- Include textual diagram descriptions (ASCII or step labels).\n";
            case "ANALYTICAL"  -> "- Include numbers, formulas, or data where possible.\n";
            case "NARRATIVE"   -> "- Frame the explanation as a story or discovery narrative.\n";
            case "HANDS_ON"    -> "- Suggest a mini-experiment or physical activity to reinforce.\n";
            default            -> "";
        });

        if (goal.equals("PASS_EXAMS")) {
            sb.append("- End with: 'Exam tip: examiners often ask about...'\n");
        }
        if (goal.equals("QUICK_REVISION")) {
            sb.append("- Keep explanation to bullet points. Speed and clarity over depth.\n");
        }

        return sb.toString();
    }
}
