package io.squados.eval;

import io.squados.annotation.EvalCriteria;
import java.util.*;

/**
 * The result of evaluating one agent output.
 * Contains per-criterion scores and an overall score.
 */
public class EvalScore {

    private final Map<EvalCriteria, Float> scores;
    private final String                   feedback;
    private final int                      attempt;
    private final String                   agentOutput;

    public EvalScore(Map<EvalCriteria, Float> scores,
                     String feedback,
                     int attempt,
                     String agentOutput) {
        this.scores      = Collections.unmodifiableMap(new LinkedHashMap<>(scores));
        this.feedback    = feedback;
        this.attempt     = attempt;
        this.agentOutput = agentOutput;
    }

    /** Average score across all criteria. 0.0 to 1.0. */
    public float overall() {
        if (scores.isEmpty()) return 0f;
        return (float) scores.values().stream()
            .mapToDouble(Float::doubleValue).average().orElse(0.0);
    }

    public Map<EvalCriteria, Float> getScores()      { return scores; }
    public String                   getFeedback()    { return feedback; }
    public int                      getAttempt()     { return attempt; }
    public String                   getAgentOutput() { return agentOutput; }
    public float                    get(EvalCriteria c) {
        return scores.getOrDefault(c, 0f);
    }

    public boolean passes(float minScore) { return overall() >= minScore; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("EvalScore{overall=%.2f, attempt=%d}%n",
            overall(), attempt));
        scores.forEach((c, s) ->
            sb.append(String.format("  %s: %.2f%n", c, s)));
        if (feedback != null && !feedback.isBlank()) {
            sb.append("  feedback: ").append(feedback).append("\n");
        }
        return sb.toString();
    }
}