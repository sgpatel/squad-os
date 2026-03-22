package io.squados.approval;

import io.squados.annotation.AutoApproval;
import io.squados.annotation.AwaitApproval;
import io.squados.exception.ApprovalRejectedException;
import io.squados.exception.AutoApprovalFailedException;

import java.lang.reflect.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates {@literal @}AutoApproval conditions and orchestrates
 * {@literal @}AwaitApproval human review flows.
 *
 * Decision flow for a method annotated with both:
 * <pre>
 *   {@literal @}AutoApproval(condition = "amount < 10000")
 *   {@literal @}AwaitApproval(reason = "Exceeds auto-approval limit")
 * </pre>
 *
 * 1. Method executes normally, returns result
 * 2. ApprovalEngine evaluates @AutoApproval condition against result
 * 3a. Condition passes (amount < 10000) -> AUTO_APPROVED, return immediately
 * 3b. Condition fails (amount >= 10000) -> fall through to @AwaitApproval
 * 4. @AwaitApproval: persist, notify approver, block thread
 * 5. Human approves -> return result  |  rejects -> throw exception
 *
 * Condition syntax (simple expression evaluator):
 *   "amount < 10000"                    field comparison
 *   "amount < 10000 AND riskScore < 0.3" compound AND
 *   "status == APPROVED"                string equality
 *   "amount < 10000 OR amount == 0"     compound OR
 */
public class ApprovalEngine {

    private final ApprovalStore store;
    private final long          pollMs;

    public ApprovalEngine(ApprovalStore store) {
        this(store, 500L);
    }

    public ApprovalEngine(ApprovalStore store, long pollMs) {
        this.store  = store;
        this.pollMs = pollMs;
    }

    public ApprovalStore getStore() { return store; }

    /**
     * Process approval annotations on a method after it has returned a result.
     *
     * @param method     The method that was called
     * @param result     The return value of the method
     * @param agentName  Name of the agent for logging
     * @return           The result (unchanged) if approved
     * @throws ApprovalRejectedException   if human rejects
     * @throws AutoApprovalFailedException if @AutoApproval fails with no fallback
     */
    public Object processApprovals(Method method, Object result, String agentName) {
        AutoApproval  autoAnn  = method.getAnnotation(AutoApproval.class);
        AwaitApproval awaitAnn = method.getAnnotation(AwaitApproval.class);

        // No approval annotations — pass through
        if (autoAnn == null && awaitAnn == null) return result;

        String decision = result == null ? "null" : result.toString();

        // ── @AutoApproval evaluation ──────────────────────────────
        if (autoAnn != null) {
            boolean conditionMet = evaluateCondition(autoAnn.condition(), result);
            boolean shouldApprove = conditionMet != autoAnn.rejectOnMatch();

            if (shouldApprove) {
                System.out.printf("[ApprovalEngine] AUTO_APPROVED: %s.%s() — %s%n",
                    agentName, method.getName(), autoAnn.reason());
                // Create a record for audit trail
                ApprovalRequest req = new ApprovalRequest(
                    agentName, method.getName(), decision,
                    autoAnn.reason(), "system",
                    io.squados.annotation.ApprovalPriority.LOW,
                    io.squados.annotation.TimeoutPolicy.REJECT, 0
                );
                req.autoApprove(autoAnn.reason());
                store.save(req);
                return result; // Auto-approved — return immediately
            } else {
                System.out.printf("[ApprovalEngine] AutoApproval condition failed: %s%n",
                    autoAnn.condition());
                // Falls through to @AwaitApproval if present
                if (awaitAnn == null) {
                    throw new AutoApprovalFailedException(
                        autoAnn.condition(), autoAnn.rejectReason());
                }
            }
        }

        // ── @AwaitApproval human review ───────────────────────────
        if (awaitAnn != null) {
            ApprovalRequest req = new ApprovalRequest(
                agentName, method.getName(), decision,
                awaitAnn.reason(), awaitAnn.escalateTo(),
                awaitAnn.priority(), awaitAnn.onTimeout(),
                awaitAnn.timeoutHours()
            );
            store.save(req);
            notifyApprover(req);

            ApprovalRequest.Status status = store.awaitDecision(req.getId(), pollMs);

            return switch (status) {
                case APPROVED, AUTO_APPROVED -> result;
                case REJECTED -> throw new ApprovalRejectedException(
                    req.getId(),
                    req.getReviewNote() != null ? req.getReviewNote() : awaitAnn.reason());
                case TIMED_OUT -> switch (awaitAnn.onTimeout()) {
                    case APPROVE -> result;
                    case ESCALATE -> result; // Escalation handled externally
                    default -> throw new ApprovalRejectedException(
                        req.getId(), "Approval request timed out");
                };
                default -> throw new ApprovalRejectedException(req.getId(), "Unknown status: " + status);
            };
        }

        return result;
    }

    // ── Condition evaluator ───────────────────────────────────────

    /**
     * Simple condition evaluator supporting AND, OR, and comparison operators.
     * Extracts field values from the result object via reflection.
     *
     * Examples:
     *   "amount < 10000"
     *   "amount < 10000 AND riskScore < 0.3"
     *   "status == APPROVED"
     */
    public boolean evaluateCondition(String condition, Object result) {
        if (condition == null || condition.isBlank()) return true;

        // Extract all field values from result into a map
        Map<String, Object> fields = extractFields(result);

        // Split on AND / OR
        if (condition.contains(" AND ")) {
            for (String part : condition.split(" AND ")) {
                if (!evaluateSingle(part.trim(), fields)) return false;
            }
            return true;
        }
        if (condition.contains(" OR ")) {
            for (String part : condition.split(" OR ")) {
                if (evaluateSingle(part.trim(), fields)) return true;
            }
            return false;
        }
        return evaluateSingle(condition.trim(), fields);
    }

    private boolean evaluateSingle(String expr, Map<String, Object> fields) {
        // Supported: field OP value
        // Operators: <, >, <=, >=, ==, !=
        String[] ops = {"<=", ">=", "!=", "<", ">", "=="};
        for (String op : ops) {
            if (!expr.contains(op)) continue;
            String[] parts = expr.split(op, 2);
            if (parts.length != 2) continue;
            String fieldName = parts[0].trim();
            String expected  = parts[1].trim();
            Object actual    = fields.get(fieldName);
            if (actual == null) return false;
            return compare(actual.toString(), op, expected);
        }
        // Bare boolean field
        Object val = fields.get(expr);
        return val != null && Boolean.parseBoolean(val.toString());
    }

    private boolean compare(String actual, String op, String expected) {
        try {
            double a = Double.parseDouble(actual);
            double e = Double.parseDouble(expected);
            return switch (op) {
                case "<"  -> a < e;
                case ">"  -> a > e;
                case "<=" -> a <= e;
                case ">=" -> a >= e;
                case "==" -> a == e;
                case "!=" -> a != e;
                default   -> false;
            };
        } catch (NumberFormatException e) {
            // String comparison
            return switch (op) {
                case "==" -> actual.equalsIgnoreCase(expected);
                case "!=" -> !actual.equalsIgnoreCase(expected);
                default   -> false;
            };
        }
    }

    private Map<String, Object> extractFields(Object obj) {
        Map<String, Object> fields = new ConcurrentHashMap<>();
        if (obj == null) return fields;
        // If it is a primitive wrapper or String, map as "value"
        if (obj instanceof Number || obj instanceof Boolean || obj instanceof String) {
            fields.put("value", obj);
            return fields;
        }
        // Extract public fields via reflection
        for (Field f : obj.getClass().getFields()) {
            try { fields.put(f.getName(), f.get(obj)); }
            catch (IllegalAccessException ignored) {}
        }
        // Extract via getters
        for (Method m : obj.getClass().getMethods()) {
            String name = m.getName();
            if (m.getParameterCount() != 0) continue;
            if (name.startsWith("get") && name.length() > 3) {
                String field = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                try { fields.put(field, m.invoke(obj)); }
                catch (Exception ignored) {}
            }
        }
        return fields;
    }

    private void notifyApprover(ApprovalRequest req) {
        // Pluggable notification — override this method to integrate
        // Slack, email, webhook, PagerDuty etc.
        System.out.printf("[ApprovalEngine] PENDING APPROVAL (%s)%n", req.getPriority());
        System.out.printf("  ID:          %s%n", req.getId());
        System.out.printf("  Agent:       %s.%s()%n", req.getAgentName(), req.getMethodName());
        System.out.printf("  Decision:    %s%n", req.getDecision());
        System.out.printf("  Reason:      %s%n", req.getReason());
        System.out.printf("  Approver:    %s%n", req.getEscalateTo());
        System.out.printf("  Expires:     %s%n", req.getExpiresAt());
        System.out.printf("  To approve:  approvalStore.approve(\"%s\", \"note\")%n", req.getId());
        System.out.printf("  To reject:   approvalStore.reject(\"%s\", \"reason\")%n", req.getId());
    }
}