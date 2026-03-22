package io.squados.improve;

import io.squados.annotation.Improve;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Retrieves feedback examples and builds few-shot prompt enrichment.
 *
 * Called by AgentWrapper before each LLM call when @Improve is present.
 * Injects similar past examples into the system prompt so the agent
 * learns from feedback without retraining the base model.
 *
 * Flow:
 * <pre>
 *   1. Human calls improveStore.save(new FeedbackExample(label, input, output, GOOD, note))
 *   2. On next agent call: ImproveEngine.buildEnrichment(method, input) called
 *   3. Returns few-shot prompt prefix with top-K similar examples
 *   4. Prefix is prepended to agent system prompt
 *   5. Agent sees real examples of good (and optionally bad) outputs
 *   6. Output quality improves measurably over time
 * </pre>
 */
public class ImproveEngine {

    private final FeedbackStore store;

    public ImproveEngine(FeedbackStore store) {
        this.store = store;
    }

    public FeedbackStore getStore() { return store; }

    /**
     * Build the few-shot prompt enrichment for a @Improve annotated method.
     *
     * @param method The @Improve annotated method
     * @param input  Current agent input
     * @return Prompt prefix with few-shot examples, or empty string if none
     */
    public String buildEnrichment(Method method, String input) {
        Improve ann = method.getAnnotation(Improve.class);
        if (ann == null) return "";

        String label = resolveLabel(ann, method);
        int    topK  = ann.topK();
        int    minEx = ann.minExamples();

        // Check if we have enough examples
        int totalExamples = store.count(label);
        if (totalExamples < minEx) {
            System.out.printf("[Improve] Not enough examples yet (%d/%d) for %s%n",
                totalExamples, minEx, label);
            return "";
        }

        // Retrieve similar good examples
        List<FeedbackExample> goodExamples = store.findSimilarGood(label, input, topK);

        if (goodExamples.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== FEW-SHOT EXAMPLES FROM PAST FEEDBACK ===\n");
        sb.append("The following examples show what GOOD responses look like for this task.\n");
        sb.append("Learn from these and produce similarly high-quality output.\n\n");

        for (int i = 0; i < goodExamples.size(); i++) {
            sb.append("--- Example ").append(i + 1).append(" ---\n");
            sb.append(goodExamples.get(i).toPromptExample(false));
            sb.append("\n");
        }

        // Optionally inject bad examples as negative examples
        if (ann.includeNegativeExamples()) {
            List<FeedbackExample> badExamples = store.findSimilarBad(label, input, topK);
            if (!badExamples.isEmpty()) {
                sb.append("The following examples show what to AVOID:\n\n");
                for (FeedbackExample bad : badExamples) {
                    sb.append(bad.toPromptExample(true)).append("\n");
                }
            }
        }

        sb.append("=== END EXAMPLES ===\n");

        System.out.printf("[Improve] Injecting %d few-shot example(s) for %s%n",
            goodExamples.size(), label);
        return sb.toString();
    }

    /**
     * Save a feedback example to the store.
     * Call this after a human has reviewed the agent output.
     */
    public FeedbackExample saveFeedback(Method method, String input,
                                         String output,
                                         FeedbackExample.Label label,
                                         String note) {
        Improve ann = method.getAnnotation(Improve.class);
        String methodLabel = ann != null
            ? resolveLabel(ann, method)
            : method.getDeclaringClass().getSimpleName() + "." + method.getName();

        FeedbackExample example = new FeedbackExample(
            methodLabel, input, output, label, note);
        store.save(example);
        return example;
    }

    private String resolveLabel(Improve ann, Method method) {
        return ann.label().isEmpty()
            ? method.getDeclaringClass().getSimpleName() + "." + method.getName()
            : ann.label();
    }
}