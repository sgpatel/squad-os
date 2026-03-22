package io.squados.eval;

import io.squados.annotation.EvalCriteria;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;

import java.util.*;
import java.util.regex.*;

/**
 * Uses an LLM to score agent output across evaluation criteria.
 *
 * The judge receives the original task (input) and the agent output,
 * then scores each criterion from 0.0 to 1.0 with written feedback.
 *
 * Prompt format:
 * <pre>
 *   You are an expert evaluator. Score the following response:
 *   TASK: [original input]
 *   RESPONSE: [agent output]
 *   Score each criterion from 0.0 to 1.0:
 *   FAITHFULNESS: [score] - [reason]
 *   COMPLETENESS: [score] - [reason]
 *   RELEVANCE: [score] - [reason]
 * </pre>
 */
public class EvalJudge {

    private final LlmPort llm;

    public EvalJudge(LlmPort llm) {
        this.llm = llm;
    }

    /**
     * Score an agent output against the original input.
     *
     * @param input      The original task/question given to the agent
     * @param output     The agent response to evaluate
     * @param criteria   Which criteria to score
     * @param attempt    Which retry attempt this is (for logging)
     * @return           EvalScore with per-criterion scores and feedback
     */
    public EvalScore evaluate(String input, String output,
                              EvalCriteria[] criteria, int attempt) {
        String systemPrompt = buildJudgePrompt(criteria);
        String userMessage  = buildUserMessage(input, output);

        LlmOptions opts = new LlmOptions(0.1f, 512, null); // low temp for consistent scoring
        LlmResponse response = llm.chat(systemPrompt, userMessage, opts);
        String judgeOutput = response.content();

        Map<EvalCriteria, Float> scores   = parseScores(judgeOutput, criteria);
        String                   feedback = parseFeedback(judgeOutput);

        EvalScore score = new EvalScore(scores, feedback, attempt, output);
        System.out.printf("[Eval] Attempt %d — overall: %.2f %s%n",
            attempt, score.overall(), formatScores(scores));
        return score;
    }

    /**
     * Score using a mock judge (for testing without LLM).
     * Accepts a map of criterion -> score directly.
     */
    public static EvalScore mockScore(Map<EvalCriteria, Float> scores,
                                      String feedback, int attempt,
                                      String output) {
        return new EvalScore(scores, feedback, attempt, output);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private String buildJudgePrompt(EvalCriteria[] criteria) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert AI output evaluator. ");
        sb.append("Score the agent response on the following criteria.\n");
        sb.append("For each criterion, respond with: CRITERION_NAME: 0.X - brief reason\n\n");
        sb.append("Criteria to score:\n");
        for (EvalCriteria c : criteria) {
            sb.append("  ").append(c.name()).append(": ").append(criterionDescription(c)).append("\n");
        }
        sb.append("\nBe strict. Score 0.0 for poor, 0.5 for acceptable, 1.0 for excellent.");
        return sb.toString();
    }

    private String buildUserMessage(String input, String output) {
        return "ORIGINAL TASK:\n" + input +
               "\n\nAGENT RESPONSE:\n" + output +
               "\n\nProvide your scores now:";
    }

    private Map<EvalCriteria, Float> parseScores(String judgeOutput,
                                                  EvalCriteria[] criteria) {
        Map<EvalCriteria, Float> scores = new LinkedHashMap<>();
        for (EvalCriteria c : criteria) {
            float score = extractScore(judgeOutput, c.name());
            scores.put(c, score);
        }
        return scores;
    }

    /**
     * Extract a score like "FAITHFULNESS: 0.8" from judge output.
     * Returns 0.5 (neutral) if not found.
     */
    public float extractScore(String text, String criterionName) {
        Pattern p = Pattern.compile(
            criterionName + "\\s*:\\s*(-?[0-9]*\\.?[0-9]+)",
            Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            try {
                float score = Float.parseFloat(m.group(1));
                return Math.max(0f, Math.min(1f, score)); // clamp to [0,1]
            } catch (NumberFormatException ignored) {}
        }
        return 0.5f; // neutral default if not found
    }

    private String parseFeedback(String judgeOutput) {
        // Return the full judge output as feedback (trimmed)
        return judgeOutput.length() > 500
            ? judgeOutput.substring(0, 500) + "..." : judgeOutput;
    }

    private String criterionDescription(EvalCriteria c) {
        return switch (c) {
            case FAITHFULNESS  -> "Does the response accurately reflect the input? (no hallucinations)";
            case COMPLETENESS  -> "Does the response cover all required aspects?";
            case RELEVANCE     -> "Is the response relevant to the question asked?";
            case CLARITY       -> "Is the response clear and well-structured?";
            case CORRECTNESS   -> "Is the response factually correct?";
            case SAFETY        -> "Is the response free of harmful content?";
        };
    }

    private String formatScores(Map<EvalCriteria, Float> scores) {
        StringBuilder sb = new StringBuilder("[");
        scores.forEach((c, s) -> sb.append(c.name().substring(0,3)).append(":").append(String.format("%.1f", s)).append(" "));
        return sb.toString().trim() + "]";
    }
}