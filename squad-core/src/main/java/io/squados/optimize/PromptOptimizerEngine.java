package io.squados.optimize;

import io.squados.annotation.EvalCriteria;
import io.squados.annotation.OptimizePrompt;
import io.squados.benchmark.BenchmarkCase;
import io.squados.context.AgentWrapper;
import io.squados.context.SquadContext;
import io.squados.eval.EvalJudge;
import io.squados.eval.EvalScore;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DSPy-style automated prompt optimizer.
 *
 * Algorithm per iteration:
 *   1. Run agent on all training examples with the current prompt
 *   2. Compute average score via EvalJudge
 *   3. If score ≥ threshold → stop (converged)
 *   4. Collect failure cases (score below threshold)
 *   5. Ask an LLM to analyse failure patterns and propose a prompt rewrite
 *   6. Re-evaluate the new prompt
 *   7. Accept if improvement ≥ minImprovement, else keep current
 *   8. Repeat
 *
 * Usage:
 * <pre>
 *   PromptOptimizerEngine opt = new PromptOptimizerEngine(ctx, judgePort, store);
 *   List&lt;BenchmarkCase&gt; examples = List.of(
 *       BenchmarkCase.of("Analyse: 'Great product!'", "POSITIVE"),
 *       BenchmarkCase.of("Analyse: 'Terrible.'",      "NEGATIVE")
 *   );
 *   PromptOptimizationResult result = opt.optimize(SentimentAgent.class, examples);
 *   System.out.println(result.bestPrompt());
 * </pre>
 */
public class PromptOptimizerEngine {

    private final SquadContext       ctx;
    private final LlmPort            judgePort;
    private final PromptVersionStore store;

    public PromptOptimizerEngine(SquadContext ctx, LlmPort judgePort, PromptVersionStore store) {
        this.ctx       = ctx;
        this.judgePort = judgePort;
        this.store     = store;
    }

    public PromptOptimizerEngine(SquadContext ctx, LlmPort judgePort) {
        this(ctx, judgePort, new PromptVersionStore());
    }

    // ── Main API ──────────────────────────────────────────────────────

    /**
     * Optimize the prompt for {@code agentClass} using the given training examples.
     * Reads optimization parameters from the {@literal @}OptimizePrompt annotation.
     */
    public PromptOptimizationResult optimize(Class<?> agentClass,
                                             List<BenchmarkCase> examples) {
        OptimizePrompt ann = agentClass.getAnnotation(OptimizePrompt.class);
        if (ann == null) throw new IllegalArgumentException(
            agentClass.getSimpleName() + " is not annotated with @OptimizePrompt");

        return optimize(agentClass, examples,
            ann.scoreThreshold(), ann.maxIterations(),
            ann.criteria(), ann.minImprovement());
    }

    /** Optimize with explicit parameters (no annotation required). */
    public PromptOptimizationResult optimize(Class<?> agentClass,
                                             List<BenchmarkCase> examples,
                                             float scoreThreshold,
                                             int maxIterations,
                                             EvalCriteria[] criteria,
                                             float minImprovement) {
        String agentName = resolveAgentName(agentClass);
        AgentWrapper wrapper = ctx.getRegistry().getByName(agentName) != null
            ? ctx.getRegistry().getByName(agentName) : null;

        // Build initial prompt from the wrapper or a default
        String currentPrompt = wrapper != null
            ? wrapper.buildSystemPrompt(new io.squados.agent.TaskContext("optimize"))
            : "You are a helpful " + agentName + ". Respond clearly and accurately.";

        EvalJudge judge = new EvalJudge(judgePort);
        List<PromptVersion> history = new ArrayList<>();
        float initialScore = evaluate(currentPrompt, examples, criteria, judge);

        System.out.printf("[PromptOptimizer] Start — agent='%s', score=%.3f, target=%.3f%n",
            agentName, initialScore, scoreThreshold);

        PromptVersion best = PromptVersion.of(0, currentPrompt, initialScore, "initial");
        history.add(best);
        store.save(agentName, best);

        boolean reached = initialScore >= scoreThreshold;

        for (int iter = 1; iter <= maxIterations && !reached; iter++) {
            // Collect failures for analysis
            List<FailureCase> failures = collectFailures(
                currentPrompt, examples, criteria, judge, scoreThreshold);

            if (failures.isEmpty()) {
                System.out.printf("[PromptOptimizer] No failures at iter %d — converged.%n", iter);
                reached = true;
                break;
            }

            // Ask LLM to propose a prompt rewrite
            String candidatePrompt = proposeRewrite(currentPrompt, failures, agentName);

            // Evaluate the candidate
            float candidateScore = evaluate(candidatePrompt, examples, criteria, judge);

            System.out.printf("[PromptOptimizer] Iter %d — candidate score=%.3f (current=%.3f)%n",
                iter, candidateScore, best.score());

            PromptVersion candidate = PromptVersion.of(iter, candidatePrompt, candidateScore,
                "iter-" + iter + " rewrite");
            history.add(candidate);
            store.save(agentName, candidate);

            if (candidate.betterThan(best, minImprovement)) {
                best          = candidate;
                currentPrompt = candidatePrompt;
                System.out.printf("[PromptOptimizer] Accepted (Δ=+%.3f)%n",
                    candidateScore - best.score() + minImprovement);
            } else {
                System.out.printf("[PromptOptimizer] Rejected (improvement %.3f < min %.3f)%n",
                    candidateScore - best.score(), minImprovement);
            }

            reached = best.score() >= scoreThreshold;
        }

        System.out.printf("[PromptOptimizer] Done — best=%.3f, reached=%b, iter=%d%n",
            best.score(), reached, history.size() - 1);

        return new PromptOptimizationResult(
            agentName, best.prompt(), best.score(), initialScore,
            history.size() - 1, reached, List.copyOf(history));
    }

    // ── Internals ─────────────────────────────────────────────────────

    private float evaluate(String prompt, List<BenchmarkCase> examples,
                           EvalCriteria[] criteria, EvalJudge judge) {
        if (examples.isEmpty()) return 0f;
        float total = 0f;
        for (BenchmarkCase ex : examples) {
            // Run agent — use the judgePort directly with the custom prompt to simulate
            // what the agent would produce with this prompt
            LlmOptions opts = new LlmOptions(0.3f, 1024, null);
            LlmResponse r = judgePort.chat(prompt, ex.input(), opts);
            String actual = r != null && r.content() != null ? r.content() : "";
            EvalScore score = judge.evaluate(ex.input(), actual, criteria, 1);
            total += score.overall();
        }
        return total / examples.size();
    }

    private List<FailureCase> collectFailures(String prompt, List<BenchmarkCase> examples,
                                              EvalCriteria[] criteria, EvalJudge judge,
                                              float threshold) {
        List<FailureCase> failures = new ArrayList<>();
        for (BenchmarkCase ex : examples) {
            LlmOptions opts = new LlmOptions(0.3f, 1024, null);
            LlmResponse r = judgePort.chat(prompt, ex.input(), opts);
            String actual = r != null && r.content() != null ? r.content() : "";
            EvalScore score = judge.evaluate(ex.input(), actual, criteria, 1);
            if (score.overall() < threshold) {
                failures.add(new FailureCase(ex.input(), ex.expected(), actual, score));
            }
        }
        return failures;
    }

    private String proposeRewrite(String currentPrompt, List<FailureCase> failures,
                                  String agentName) {
        String failureSummary = failures.stream()
            .limit(5) // avoid token explosion
            .map(f -> String.format(
                "  Input:    %s%n  Expected: %s%n  Actual:   %s%n  Score:    %.2f",
                f.input().substring(0, Math.min(100, f.input().length())),
                f.expected().substring(0, Math.min(100, f.expected().length())),
                f.actual().substring(0, Math.min(100, f.actual().length())),
                f.score().overall()))
            .collect(Collectors.joining("\n\n"));

        String metaPrompt =
            "You are a prompt engineering expert.\n\n"
            + "Current system prompt for agent '" + agentName + "':\n"
            + "---\n" + currentPrompt + "\n---\n\n"
            + "The agent is producing suboptimal outputs on the following cases:\n"
            + failureSummary + "\n\n"
            + "Analyse the failure patterns and rewrite the system prompt to address them.\n"
            + "Return ONLY the new system prompt text — no explanation, no wrapping.";

        LlmOptions opts = new LlmOptions(0.4f, 2048, null);
        LlmResponse r = judgePort.chat(
            "You are a prompt engineering expert.", metaPrompt, opts);
        return r != null && r.content() != null ? r.content().trim() : currentPrompt;
    }

    private static String resolveAgentName(Class<?> cls) {
        var ann = cls.getAnnotation(io.squados.annotation.Agent.class);
        return ann != null && !ann.name().isBlank() ? ann.name() : cls.getSimpleName();
    }

    /** Local record for failure analysis. */
    private record FailureCase(String input, String expected, String actual, EvalScore score) {}
}
