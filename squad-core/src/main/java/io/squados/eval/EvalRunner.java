package io.squados.eval;

import io.squados.annotation.*;
import io.squados.exception.EvalFailedException;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Orchestrates the @Eval quality gate:
 *   1. Agent produces output
 *   2. EvalJudge scores it
 *   3. If score &gt;= minScore: return output
 *   4. If score &lt; minScore and retryOnFail: retry agent with feedback
 *   5. After maxRetries: apply onFail policy
 */
public class EvalRunner {

    private final EvalJudge judge;
    private final LlmPort   agentLlm;

    public EvalRunner(EvalJudge judge, LlmPort agentLlm) {
        this.judge    = judge;
        this.agentLlm = agentLlm;
    }

    /**
     * Run the eval loop for a method annotated with @Eval.
     *
     * @param method      The @Eval annotated method
     * @param agentOutput The initial agent output (from first execution)
     * @param originalInput The task input (for context in retry prompts)
     * @param systemPrompt  The agent system prompt (for retries)
     * @return             Final approved output string
     */
    public String runEvalLoop(Method method,
                              String agentOutput,
                              String originalInput,
                              String systemPrompt) {
        Eval ann = method.getAnnotation(Eval.class);
        if (ann == null) return agentOutput; // no @Eval — pass through

        float    minScore   = ann.minScore();
        int      maxRetries = ann.maxRetries();
        boolean  retry      = ann.retryOnFail();
        EvalCriteria[] criteria = ann.criteria();

        EvalScore bestScore = null;
        String    bestOutput = agentOutput;
        String    currentOutput = agentOutput;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            EvalScore score = judge.evaluate(
                originalInput, currentOutput, criteria, attempt);

            // Track best attempt
            if (bestScore == null || score.overall() > bestScore.overall()) {
                bestScore  = score;
                bestOutput = currentOutput;
            }

            if (score.passes(minScore)) {
                System.out.printf("[Eval] PASSED on attempt %d (%.2f >= %.2f)%n",
                    attempt, score.overall(), minScore);
                return currentOutput;
            }

            System.out.printf("[Eval] FAILED attempt %d (%.2f < %.2f) — %s%n",
                attempt, score.overall(), minScore,
                retry && attempt <= maxRetries ? "retrying..." : "giving up");

            if (!retry || attempt > maxRetries) break;

            // Retry: inject feedback into the prompt
            currentOutput = retryWithFeedback(
                systemPrompt, originalInput, currentOutput,
                score.getFeedback(), criteria);
        }

        // All retries exhausted
        return switch (ann.onFail()) {
            case THROW -> throw new EvalFailedException(
                method.getName(), bestScore, minScore, maxRetries);
            case RETURN_BEST -> {
                System.out.printf("[Eval] Returning best attempt (%.2f) below threshold (%.2f)%n",
                    bestScore != null ? bestScore.overall() : 0f, minScore);
                yield bestOutput;
            }
        };
    }

    /**
     * Retry the agent with the judge feedback injected into the prompt.
     */
    private String retryWithFeedback(String systemPrompt,
                                      String input,
                                      String previousOutput,
                                      String feedback,
                                      EvalCriteria[] criteria) {
        String retryPrompt = input +
            "\n\nYour previous response was evaluated and scored low.\n" +
            "Evaluator feedback:\n" + feedback +
            "\n\nPlease improve your response addressing the feedback above.";

        LlmOptions opts = new LlmOptions(0.5f, 2048, null);
        LlmResponse response = agentLlm.chat(systemPrompt, retryPrompt, opts);
        return response.content();
    }
}