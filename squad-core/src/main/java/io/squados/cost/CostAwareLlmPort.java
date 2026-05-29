package io.squados.cost;

import io.squados.annotation.CostPolicy;
import io.squados.llm.*;

import java.util.List;

/**
 * LlmPort decorator that transparently switches models based on a cost budget.
 *
 * Wraps any {@link LlmPort} delegate and, before each call, checks whether
 * the agent's spend within the current sliding window has exceeded
 * {@code CostPolicy.degradeAt * CostPolicy.budgetCentsPerHour}.
 *
 * If the threshold is exceeded and a {@code fallbackModel} is configured,
 * the call is made with the fallback model (by constructing a new
 * {@link LlmOptions} — records are immutable).
 *
 * Cost is recorded after every call using the model actually used.
 */
public class CostAwareLlmPort implements LlmPort {

    private final LlmPort    delegate;
    private final CostTracker tracker;
    private final CostPolicy  policy;
    private final String      agentName;

    /** Window size matches the budget period: 1 hour = 3,600,000 ms. */
    private static final long WINDOW_MS = 3_600_000L;

    public CostAwareLlmPort(LlmPort delegate,
                             CostTracker tracker,
                             CostPolicy policy,
                             String agentName) {
        this.delegate  = delegate;
        this.tracker   = tracker;
        this.policy    = policy;
        this.agentName = agentName;
    }

    // ── LlmPort implementation ────────────────────────────────────────

    @Override
    public LlmResponse chat(String systemPrompt, String userMessage, LlmOptions options) {
        LlmOptions effectiveOptions = resolveOptions(options);
        LlmResponse response = delegate.chat(systemPrompt, userMessage, effectiveOptions);
        recordCost(response, effectiveOptions.model());
        return response;
    }

    @Override
    public LlmResponse chatWithHistory(String systemPrompt, String userMessage,
                                        List<ConversationMessage> history,
                                        LlmOptions options) {
        LlmOptions effectiveOptions = resolveOptions(options);
        LlmResponse response = delegate.chatWithHistory(
            systemPrompt, userMessage, history, effectiveOptions);
        recordCost(response, effectiveOptions.model());
        return response;
    }

    @Override
    public void chatStream(String systemPrompt, String userMessage,
                           LlmOptions options, TokenWriter writer) {
        LlmOptions effectiveOptions = resolveOptions(options);
        delegate.chatStream(systemPrompt, userMessage, effectiveOptions, writer);
        // Streaming responses don't return token counts — record a minimal entry
        tracker.record(agentName, effectiveOptions.model() != null ? effectiveOptions.model() : "unknown", 0, 0);
    }

    @Override
    public <T> T chatStructured(String systemPrompt, String userMessage,
                                Class<T> responseType, LlmOptions options) {
        LlmOptions effectiveOptions = resolveOptions(options);
        T result = delegate.chatStructured(systemPrompt, userMessage, responseType, effectiveOptions);
        tracker.record(agentName, effectiveOptions.model() != null ? effectiveOptions.model() : "unknown", 0, 0);
        return result;
    }

    // ── Budget logic ──────────────────────────────────────────────────

    /**
     * Determine the effective LlmOptions for this call.
     * Switches to fallbackModel when the budget threshold is exceeded.
     */
    public LlmOptions resolveOptions(LlmOptions original) {
        // Unlimited budget or no fallback configured
        if (policy.budgetCentsPerHour() <= 0.0 || policy.fallbackModel().isBlank()) {
            return applyPrimaryModel(original);
        }

        double spent     = tracker.spentCents(agentName, WINDOW_MS);
        double threshold = policy.budgetCentsPerHour() * policy.degradeAt();

        if (spent >= threshold) {
            String fallback = policy.fallbackModel();
            if (fallback != null && !fallback.isBlank()) {
                System.out.printf(
                    "[CostPolicy] Agent '%s' budget threshold reached (%.4f¢ / %.4f¢) "
                    + "— switching to %s%n",
                    agentName, spent, threshold, fallback);
                return new LlmOptions(original.temperature(), original.maxTokens(), fallback);
            }
        }

        return applyPrimaryModel(original);
    }

    private LlmOptions applyPrimaryModel(LlmOptions original) {
        String primary = policy.primaryModel();
        if (primary != null && !primary.isBlank()) {
            return new LlmOptions(original.temperature(), original.maxTokens(), primary);
        }
        return original;  // inherit from squad.yml
    }

    private void recordCost(LlmResponse response, String model) {
        String usedModel = response.model() != null ? response.model()
                         : (model != null ? model : "unknown");
        tracker.record(agentName, usedModel, response.promptTokens(), response.completionTokens());
    }
}
