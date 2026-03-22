package io.squados.security;

import io.squados.annotation.AccessMode;
import io.squados.annotation.SecureAgent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Enforces @SecureAgent access control on method calls.
 *
 * Flow for AUTHENTICATED mode:
 *   1. Get current identity from SecurityContext
 *   2. Verify identity is not anonymous (authenticated)
 *   3. Verify identity is not expired
 *   4. Check required roles (if any)
 *   5. Check rate limit (if configured)
 *   6. Write audit log entry
 *   7. Execute the method OR throw SecurityException
 *
 * Flow for PUBLIC mode:
 *   1. Execute the method (no auth check)
 *   2. Write audit log entry (with ANONYMOUS identity)
 *
 * Usage:
 * <pre>
 * SecurityGuard guard = new SecurityGuard(new AuditLog());
 *
 * // Set identity before calling secured method
 * SecurityContext.set(AgentIdentity.of("alice", "compliance"));
 *
 * // Execute with security enforcement
 * String result = guard.execute(method, () -> agent.underwriteLoan(app));
 * </pre>
 */
public class SecurityGuard {

    private final AuditLog                        auditLog;
    private final Map<String, AtomicInteger>       rateCounts = new ConcurrentHashMap<>();

    public SecurityGuard(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    public AuditLog getAuditLog() { return auditLog; }

    /**
     * Execute a method with @SecureAgent enforcement.
     *
     * @param method The @SecureAgent annotated method
     * @param action The actual method call
     * @return Result of the method
     * @throws SecurityException if access is denied
     */
    public <T> T execute(Method method, Supplier<T> action) {
        SecureAgent ann = resolveAnnotation(method);
        if (ann == null) return action.get(); // no @SecureAgent — pass through

        AgentIdentity identity = SecurityContext.current();
        String methodName = method.getDeclaringClass().getSimpleName()
            + "." + method.getName();
        long start = System.currentTimeMillis();

        // ── PUBLIC mode — no auth check ───────────────────────────
        if (ann.mode() == AccessMode.PUBLIC) {
            T result = action.get();
            if (ann.auditLog()) {
                auditLog.record(auditLog.buildEntry(
                    identity.isAnonymous() ? AgentIdentity.ANONYMOUS : identity,
                    methodName, new String[0],
                    AuditLog.Decision.GRANTED, null,
                    System.currentTimeMillis() - start,
                    ann.logOutput() && result != null ? result.toString() : null));
            }
            return result;
        }

        // ── AUTHENTICATED mode ─────────────────────────────────────

        // Check authentication
        if (identity.isAnonymous()) {
            denyAndLog(identity, methodName, ann, start, "Not authenticated");
            throw new SecurityException("Authentication required: " + ann.denyMessage());
        }

        // Check expiry
        if (identity.isExpired()) {
            denyAndLog(identity, methodName, ann, start, "Token expired");
            throw new SecurityException("JWT token has expired: " + ann.denyMessage());
        }

        // Check required roles
        if (ann.roles().length > 0 && !identity.hasAnyRole(ann.roles())) {
            String reason = "Missing roles: requires one of " + java.util.Arrays.toString(ann.roles()) +
                ", caller has " + identity.getRoles();
            denyAndLog(identity, methodName, ann, start, reason);
            throw new SecurityException(ann.denyMessage() + " — " + reason);
        }

        // Check rate limit
        if (ann.rateLimit() > 0) {
            String rateKey = identity.getSubject() + ":" + methodName;
            AtomicInteger count = rateCounts.computeIfAbsent(
                rateKey, k -> new AtomicInteger(0));
            if (count.incrementAndGet() > ann.rateLimit()) {
                String reason = "Rate limit exceeded: " + ann.rateLimit() + "/min";
                denyAndLog(identity, methodName, ann, start, reason);
                throw new SecurityException(reason);
            }
        }

        // ── Access GRANTED — execute ──────────────────────────────
        System.out.printf("[SecureAgent] GRANTED: %s called by %s %s%n",
            methodName, identity.getSubject(), identity.getRoles());

        T result = action.get();

        if (ann.auditLog()) {
            auditLog.record(auditLog.buildEntry(
                identity, methodName, ann.roles(),
                AuditLog.Decision.GRANTED, null,
                System.currentTimeMillis() - start,
                ann.logOutput() && result != null ? result.toString() : null));
        }

        return result;
    }

    /** Clear rate limit counters (call at start of each minute in production). */
    public void resetRateLimits() { rateCounts.clear(); }

    // ── Helpers ──────────────────────────────────────────────────

    private void denyAndLog(AgentIdentity identity, String method,
                             SecureAgent ann, long start, String reason) {
        System.out.printf("[SecureAgent] DENIED: %s for %s — %s%n",
            method, identity.getSubject(), reason);
        if (ann.auditLog()) {
            auditLog.record(auditLog.buildEntry(
                identity, method, ann.roles(),
                AuditLog.Decision.DENIED, reason,
                System.currentTimeMillis() - start, null));
        }
    }

    private SecureAgent resolveAnnotation(Method method) {
        SecureAgent ann = method.getAnnotation(SecureAgent.class);
        if (ann == null)
            ann = method.getDeclaringClass().getAnnotation(SecureAgent.class);
        return ann;
    }
}