package io.tutoros.agent;

import io.squados.annotation.*;

/**
 * Intent Analyzer Agent — determines learner intent and recommends teaching style.
 *
 * Analyzes the learner's message to understand:
 *   - Are they asking for direct explanation?
 *   - Are they expressing confusion or frustration?
 *   - Are they ready for guided discovery (Socratic)?
 *   - Are they answering a previous question?
 *
 * Returns a structured recommendation: DIRECT or SOCRATIC with confidence score.
 *
 * This replaces hardcoded keyword matching with intelligent LLM-based intent detection.
 */
@Agent(
    // NOTE: Must NOT use ANALYST — DiagnosticAgent already claims that role.
    // The registry silently evicts duplicates (AgentRegistry:30-45), so a
    // collision here would route intent prompts to DiagnosticAgent (or vice
    // versa). SCOUT fits well — a fast, low-token recon classifier.
    role        = AgentRole.SCOUT,
    name        = "IntentAnalyzerAgent",
    description = "Analyzes learner messages to determine intent and recommend " +
                  "the most appropriate teaching style (direct vs Socratic)."
)
@StructuredOutput(schema = IntentAnalysis.class, retryOnMalformed = true, maxRetries = 2)
@Traced(spanName = "intent-analysis")
public class IntentAnalyzerAgent {

    /**
     * Analyzes the learner's message and conversation context to determine intent.
     *
     * @param message           The learner's current message
     * @param conversationHistory Recent conversation turns (last 3-5 exchanges)
     * @param learnerProfile    Learner level, learning style, current mastery
     * @return Structured intent analysis with teaching style recommendation
     */
    public String analyzeIntent(String message, String conversationHistory, 
                                String learnerProfile) {
        return """
            You are an educational intent analyzer. Your job is to understand what
            the learner needs right now and recommend the best teaching approach.
            
            Learner profile:
            %s
            
            Recent conversation:
            %s
            
            Current learner message: "%s"
            
            Analyze the learner's intent and recommend a teaching style.
            
            DIRECT teaching is best when:
            - Learner explicitly asks for explanation ("explain", "tell me", "what is")
            - Learner expresses confusion ("I don't know", "I'm lost", "not sure")
            - Learner is frustrated or overwhelmed
            - Learner gives very short answers suggesting they're stuck
            - Learner asks for help understanding something
            - This is foundational knowledge they need before discovery
            
            SOCRATIC teaching is best when:
            - Learner shows partial understanding and can build on it
            - Learner is engaged and answering questions thoughtfully
            - Learner's mastery is 50-80%% (almost there, needs guided discovery)
            - Learner asks "why" or "how" questions showing curiosity
            - Learner is ready to construct understanding themselves
            
            Output JSON format:
            {
              "intent": "SEEKING_EXPLANATION | CONFUSED | ANSWERING_QUESTION | CURIOUS | FRUSTRATED",
              "recommendedStyle": "DIRECT | SOCRATIC",
              "confidence": 0.0-1.0,
              "reasoning": "One sentence explaining why"
            }
            
            Be decisive. Default to DIRECT when uncertain — better to over-explain
            than frustrate a struggling learner.
            """.formatted(learnerProfile, conversationHistory, message);
    }
}
// IntentAnalysis moved to its own file (IntentAnalysis.java) so it can be
// declared public and used by other packages (e.g. io.tutoros.pipeline) via
// AgentResponse.structuredOutput(IntentAnalysis.class).
