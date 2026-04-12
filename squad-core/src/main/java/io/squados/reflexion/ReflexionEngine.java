package io.squados.reflexion;

import io.squados.annotation.EvalCriteria;
import io.squados.annotation.Reflexion;
import io.squados.eval.EvalJudge;
import io.squados.eval.EvalScore;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the Reflexion loop: score → critique → re-run.
 *
 * Called from {@link io.squados.context.AgentWrapper} after the initial LLM
 * call (step 9) when the agent class carries {@code @Reflexion}.
 *
 * The engine does NOT re-invoke the full 16-step AgentWrapper pipeline for
 * iterations — it calls the LLM directly via the shared LlmPort so that
 * rate-limits, circuit-breakers, and guardrails apply only to the first call.
 */
public class ReflexionEngine {

    private final LlmPort  llm;
    private final EvalJudge judge;

    public ReflexionEngine(LlmPort llm) {
        this.llm   = llm;
        this.judge = new EvalJudge(llm);
    }

    /**
     * Run the Reflexion loop.
     *
     * @param ann          The {@code @Reflexion} annotation on the agent class.
     * @param initialOutput First response from the agent (from the normal pipeline).
     * @param systemPrompt  System prompt used for the initial call (reused in iterations).
     * @param task          Original user task (input to the agent).
     * @return              {@link ReflexionResult} with full iteration history.
     */
    public ReflexionResult run(Reflexion ann,
                               String initialOutput,
                               String systemPrompt,
                               String task) {
        List<ReflexionIteration> history = new ArrayList<>();
        EvalCriteria[] criteria = ann.criteria();
        float threshold         = ann.scoreThreshold();
        int   maxIterations     = ann.maxIterations();

        String currentOutput = initialOutput;

        for (int i = 0; i < maxIterations + 1; i++) {
            // Score current output
            EvalScore score = judge.evaluate(task, currentOutput, criteria, i + 1);

            boolean accepted = score.overall() >= threshold;
            String critique  = null;

            if (!accepted && i < maxIterations) {
                // Generate critique for next iteration
                critique = generateCritique(task, currentOutput, score, systemPrompt);
            }

            history.add(new ReflexionIteration(i + 1, currentOutput, score, critique));

            if (accepted) {
                return new ReflexionResult(currentOutput, history, true);
            }

            if (i < maxIterations && critique != null) {
                // Re-run LLM with critique-enriched system prompt
                String enrichedPrompt = systemPrompt
                    + "\n\n## Previous Attempt Critique\n" + critique
                    + "\n\nPlease improve your response based on this critique.";
                LlmOptions opts = new LlmOptions(0.4f, 1024, null);
                LlmResponse raw = llm.chat(enrichedPrompt, task, opts);
                currentOutput = raw.content() != null ? raw.content() : currentOutput;
            }
        }

        // Threshold not reached — return best-scoring iteration
        ReflexionIteration best = history.stream()
            .max(java.util.Comparator.comparingDouble(it -> it.score().overall()))
            .orElse(history.get(0));

        return new ReflexionResult(best.output(), history, false);
    }

    // ── Critique generation ───────────────────────────────────────────

    private String generateCritique(String task, String output,
                                    EvalScore score, String systemPrompt) {
        String critiquePrompt =
            "You are a critique assistant. Given a task and an agent response, "
            + "provide a concise 2-3 sentence critique identifying specific weaknesses "
            + "and actionable improvements. Be direct and constructive.";

        String userMsg = "TASK:\n" + task
            + "\n\nAGENT RESPONSE:\n" + output
            + "\n\nCURRENT SCORES:\n" + formatScores(score)
            + "\n\nProvide your critique:";

        LlmOptions opts = new LlmOptions(0.3f, 256, null);
        LlmResponse critiqueResponse = llm.chat(critiquePrompt, userMsg, opts);
        return critiqueResponse.content() != null
               ? critiqueResponse.content()
               : "The response needs improvement in accuracy and completeness.";
    }

    private String formatScores(EvalScore score) {
        StringBuilder sb = new StringBuilder();
        score.getScores().forEach((c, s) ->
            sb.append("  ").append(c.name()).append(": ").append(String.format("%.2f", s)).append("\n"));
        sb.append("  Overall: ").append(String.format("%.2f", score.overall()));
        return sb.toString();
    }
}
