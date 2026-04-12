package io.squados.reflexion;

import io.squados.eval.EvalScore;

/**
 * A single iteration of the Reflexion loop.
 *
 * @param iteration  1-based iteration index (1 = initial call, 2+ = critique cycles).
 * @param output     Agent output produced in this iteration.
 * @param score      Eval score for this iteration's output.
 * @param critique   Critique text that was prepended on the next iteration's call.
 *                   Null on the last (accepted) iteration.
 */
public record ReflexionIteration(
        int       iteration,
        String    output,
        EvalScore score,
        String    critique
) {}
