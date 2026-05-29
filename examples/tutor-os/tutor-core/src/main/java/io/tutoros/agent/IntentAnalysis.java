package io.tutoros.agent;

import io.squados.annotation.OutputField;

/**
 * Structured output for {@link IntentAnalyzerAgent}.
 *
 * Public so callers in other packages (e.g. {@code io.tutoros.pipeline}) can
 * request this type via
 * {@code AgentResponse.structuredOutput(IntentAnalysis.class)} rather than
 * substring-matching on the raw JSON content (which is fragile — e.g. the
 * {@code reasoning} field may contain the word "DIRECT" even when
 * {@code recommendedStyle} is SOCRATIC).
 */
public class IntentAnalysis {

    @OutputField(
        description = "Detected learner intent",
        example     = "SEEKING_EXPLANATION"
    )
    public String intent;           // SEEKING_EXPLANATION | CONFUSED | ANSWERING_QUESTION | CURIOUS | FRUSTRATED

    @OutputField(
        description = "Recommended teaching style",
        example     = "DIRECT"
    )
    public String recommendedStyle; // DIRECT | SOCRATIC

    @OutputField(
        description = "Confidence in the recommendation (0.0–1.0)",
        example     = "0.85"
    )
    public double confidence;

    @OutputField(
        description = "One-sentence explanation for the recommendation"
    )
    public String reasoning;
}
