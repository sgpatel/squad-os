package io.squados.pipeline;

/**
 * Evaluates @Step.condition() expressions against a text string.
 *
 * Supported predicates:
 *   contains("x")        — text contains x (case-insensitive)
 *   notContains("x")     — text does not contain x
 *   startsWith("x")      — text starts with x
 *   endsWith("x")        — text ends with x
 *   matches("regex")     — text matches Java regex
 *   isEmpty              — text is blank
 *   isNotEmpty           — text is not blank
 *   success              — always true (used in pipeline context where step succeeded)
 *   failure              — always false (used in pipeline context where step failed)
 *
 * Compound (left-to-right, short-circuit):
 *   expr1 || expr2
 *   expr1 && expr2
 */
public class ConditionEvaluator {

    /**
     * Evaluate a condition expression against the given text.
     *
     * @param expression  The condition string from @Step.condition()
     * @param text        The text to evaluate against (typically prior step output)
     * @param stepSuccess Whether the triggering step succeeded (for 'success'/'failure' predicates)
     * @return            true if condition holds, false if step should be skipped
     */
    public static boolean evaluate(String expression, String text, boolean stepSuccess) {
        if (expression == null || expression.isBlank()) return true;

        expression = expression.trim();

        // Handle || (OR) — left-to-right, short-circuit
        if (expression.contains(" || ")) {
            String[] parts = expression.split(" \\|\\| ", 2);
            return evaluate(parts[0].trim(), text, stepSuccess)
                || evaluate(parts[1].trim(), text, stepSuccess);
        }

        // Handle && (AND) — left-to-right, short-circuit
        if (expression.contains(" && ")) {
            String[] parts = expression.split(" && ", 2);
            return evaluate(parts[0].trim(), text, stepSuccess)
                && evaluate(parts[1].trim(), text, stepSuccess);
        }

        // Single predicate
        return evaluatePredicate(expression, text, stepSuccess);
    }

    private static boolean evaluatePredicate(String expr, String text, boolean stepSuccess) {
        String t = text == null ? "" : text;

        if (expr.equals("isEmpty"))    return t.isBlank();
        if (expr.equals("isNotEmpty")) return !t.isBlank();
        if (expr.equals("success"))    return stepSuccess;
        if (expr.equals("failure"))    return !stepSuccess;

        if (expr.startsWith("contains("))    return evalStringPred(expr, "contains",    t, false);
        if (expr.startsWith("notContains(")) return evalStringPred(expr, "notContains", t, false);
        if (expr.startsWith("startsWith("))  return evalStringPred(expr, "startsWith",  t, true);
        if (expr.startsWith("endsWith("))    return evalStringPred(expr, "endsWith",    t, true);
        if (expr.startsWith("matches("))     return evalRegex(expr, t);

        // Unknown predicate — default to true (don't skip)
        return true;
    }

    private static boolean evalStringPred(String expr, String funcName,
                                           String text, boolean exact) {
        String arg = extractArg(expr);
        if (arg == null) return true;
        String lower = text.toLowerCase();
        String argLower = arg.toLowerCase();
        return switch (funcName) {
            case "contains"    -> lower.contains(argLower);
            case "notContains" -> !lower.contains(argLower);
            case "startsWith"  -> lower.startsWith(argLower);
            case "endsWith"    -> lower.endsWith(argLower);
            default            -> true;
        };
    }

    private static boolean evalRegex(String expr, String text) {
        String pattern = extractArg(expr);
        if (pattern == null) return true;
        try {
            return text.matches(pattern);
        } catch (Exception e) {
            return true; // bad regex — don't skip
        }
    }

    /** Extract the string argument from a predicate like contains("foo"). */
    static String extractArg(String expr) {
        int start = expr.indexOf("(");
        int end   = expr.lastIndexOf(")");
        if (start < 0 || end < 0 || end <= start) return null;
        String inner = expr.substring(start + 1, end).trim();
        // Strip surrounding quotes
        if ((inner.startsWith("\"") && inner.endsWith("\""))
         || (inner.startsWith("'")  && inner.endsWith("'"))) {
            return inner.substring(1, inner.length() - 1);
        }
        return inner;
    }
}
