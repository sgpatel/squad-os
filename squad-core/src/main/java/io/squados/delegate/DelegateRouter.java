package io.squados.delegate;

import io.squados.annotation.*;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Routes tasks to specialist agents based on @Delegate configuration.
 *
 * Four strategies:
 *
 * LLM_CHOICE:
 *   Asks the LLM: "Given this task, which of these roles should handle it?"
 *   Parses the role name from the LLM response.
 *   Most flexible — works for any task type.
 *
 * ROUND_ROBIN:
 *   Cycles through candidates in order.
 *   No LLM call needed — pure load distribution.
 *
 * LOAD_BALANCE:
 *   Routes to the candidate with the fewest active delegations.
 *   Uses AtomicInteger counters per role.
 *
 * FIRST_MATCH:
 *   Evaluates condition expressions against the task input.
 *   Routes to first candidate whose condition is true.
 *   Same condition syntax as @AutoApproval.
 */
public class DelegateRouter {

    private final LlmPort llm;
    private final Map<AgentRole, AtomicInteger> activeCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger>    roundRobinIdx = new ConcurrentHashMap<>();

    public DelegateRouter(LlmPort llm) {
        this.llm = llm;
    }

    /**
     * Route a task to the best candidate based on @Delegate annotation.
     *
     * @param method The @Delegate annotated method
     * @param input  The task input to route
     * @return       DelegationDecision with chosen role and reasoning
     */
    public DelegationDecision route(Method method, String input) {
        Delegate ann = method.getAnnotation(Delegate.class);
        if (ann == null || ann.candidates().length == 0) {
            return new DelegationDecision(AgentRole.STRATEGIST, new AgentRole[0],
                DelegateStrategy.LLM_CHOICE, "no @Delegate annotation", false);
        }

        AgentRole[] candidates = ann.candidates();
        DelegateStrategy strategy = ann.strategy();

        return switch (strategy) {
            case LLM_CHOICE  -> routeByLlm(input, candidates, ann.fallback());
            case ROUND_ROBIN -> routeRoundRobin(method.getName(), candidates);
            case LOAD_BALANCE -> routeByLoad(candidates, ann.fallback());
            case FIRST_MATCH -> routeByCondition(
                input, candidates, ann.conditions(), ann.fallback());
        };
    }

    /** Increment active count when delegation starts. */
    public void startDelegation(AgentRole role) {
        activeCounts.computeIfAbsent(role, r -> new AtomicInteger(0)).incrementAndGet();
    }

    /** Decrement active count when delegation completes. */
    public void endDelegation(AgentRole role) {
        AtomicInteger count = activeCounts.get(role);
        if (count != null && count.get() > 0) count.decrementAndGet();
    }

    public int getActiveCount(AgentRole role) {
        return activeCounts.getOrDefault(role, new AtomicInteger(0)).get();
    }

    // ── Strategy implementations ──────────────────────────────────

    private DelegationDecision routeByLlm(String input, AgentRole[] candidates,
                                            AgentRole fallback) {
        String roleNames = String.join(", ", Arrays.stream(candidates)
            .map(Enum::name).toArray(String[]::new));

        String prompt = "You are a task router. Given this task, choose the BEST role to handle it.\n" +
            "Available roles: " + roleNames + "\n\n" +
            "TASK:\n" + input + "\n\n" +
            "Respond with ONLY the role name (e.g. ANALYST). Nothing else.";

        LlmOptions opts = new LlmOptions(0.1f, 50, null);
        LlmResponse resp = llm.chat("You are a task router.", prompt, opts);
        String chosen = resp.content() == null ? "" : resp.content().trim().toUpperCase();

        // Try to match chosen to one of the candidates
        for (AgentRole candidate : candidates) {
            if (chosen.contains(candidate.name())) {
                System.out.printf("[Delegate] LLM_CHOICE: routed to %s%n", candidate);
                return new DelegationDecision(candidate, candidates,
                    DelegateStrategy.LLM_CHOICE,
                    "LLM chose " + candidate + " for: " + input.substring(0, Math.min(50, input.length())),
                    false);
            }
        }

        // Fallback if LLM response unclear
        System.out.printf("[Delegate] LLM_CHOICE: unclear response (%s) — using fallback %s%n",
            chosen, fallback);
        return new DelegationDecision(fallback, candidates,
            DelegateStrategy.LLM_CHOICE, "LLM unclear — fallback", true);
    }

    private DelegationDecision routeRoundRobin(String methodKey, AgentRole[] candidates) {
        AtomicInteger idx = roundRobinIdx.computeIfAbsent(methodKey, k -> new AtomicInteger(0));
        int chosen = idx.getAndUpdate(i -> (i + 1) % candidates.length);
        AgentRole role = candidates[chosen];
        System.out.printf("[Delegate] ROUND_ROBIN: routed to %s (index %d)%n", role, chosen);
        return new DelegationDecision(role, candidates,
            DelegateStrategy.ROUND_ROBIN, "Round-robin index " + chosen, false);
    }

    private DelegationDecision routeByLoad(AgentRole[] candidates, AgentRole fallback) {
        AgentRole least = Arrays.stream(candidates)
            .min(Comparator.comparingInt(
                r -> activeCounts.getOrDefault(r, new AtomicInteger(0)).get()))
            .orElse(fallback);
        int count = activeCounts.getOrDefault(least, new AtomicInteger(0)).get();
        System.out.printf("[Delegate] LOAD_BALANCE: routed to %s (active: %d)%n", least, count);
        return new DelegationDecision(least, candidates,
            DelegateStrategy.LOAD_BALANCE, "Least loaded: " + least + " (" + count + " active)", false);
    }

    private DelegationDecision routeByCondition(String input, AgentRole[] candidates,
                                                  String[] conditions, AgentRole fallback) {
        if (conditions.length == 0 || conditions.length != candidates.length) {
            System.out.println("[Delegate] FIRST_MATCH: no conditions — using fallback");
            return new DelegationDecision(fallback, candidates,
                DelegateStrategy.FIRST_MATCH, "No conditions configured", true);
        }
        // Simple keyword matching for condition evaluation
        for (int i = 0; i < conditions.length; i++) {
            if (matchesCondition(conditions[i], input)) {
                AgentRole role = candidates[i];
                System.out.printf("[Delegate] FIRST_MATCH: condition [%s] matched -> %s%n",
                    conditions[i], role);
                return new DelegationDecision(role, candidates,
                    DelegateStrategy.FIRST_MATCH,
                    "Condition matched: " + conditions[i], false);
            }
        }
        System.out.printf("[Delegate] FIRST_MATCH: no condition matched — fallback %s%n", fallback);
        return new DelegationDecision(fallback, candidates,
            DelegateStrategy.FIRST_MATCH, "No condition matched", true);
    }

    /**
     * Simple condition: "keyword1 OR keyword2" or "keyword1 AND keyword2".
     * Checks if the input contains the keywords (case-insensitive).
     */
    public boolean matchesCondition(String condition, String input) {
        if (condition == null || condition.isBlank()) return false;
        String lowerInput = input.toLowerCase();
        if (condition.contains(" OR ")) {
            for (String part : condition.split(" OR ")) {
                if (lowerInput.contains(part.trim().toLowerCase())) return true;
            }
            return false;
        }
        if (condition.contains(" AND ")) {
            for (String part : condition.split(" AND ")) {
                if (!lowerInput.contains(part.trim().toLowerCase())) return false;
            }
            return true;
        }
        return lowerInput.contains(condition.trim().toLowerCase());
    }
}