package io.squados.annotation;
import java.lang.annotation.*;

/**
 * Enforces RBAC (Role-Based Access Control) on agent method calls.
 *
 * Two modes:
 *   AUTHENTICATED — caller must present a valid JWT token with required roles
 *   PUBLIC        — no authentication required (open access)
 *
 * Features:
 *   - JWT validation (signature + expiry + issuer)
 *   - Role-based access (caller must have at least one required role)
 *   - Audit logging (every call logged with caller identity)
 *   - IP allowlist (optional — restrict to specific IP ranges)
 *   - Rate limiting per identity (optional)
 *
 * Usage:
 * <pre>
 * // Requires authentication + specific roles
 * {@literal @}SecureAgent(
 *     roles    = {"compliance", "senior-risk"},
 *     auditLog = true
 * )
 * public LoanDecision underwriteLoan(LoanApplication app) { ... }
 *
 * // Public — no auth needed
 * {@literal @}SecureAgent(mode = AccessMode.PUBLIC)
 * public String getPublicRates() { ... }
 *
 * // Admin only
 * {@literal @}SecureAgent(roles = {"admin"}, denyMessage = "Admins only!")
 * public void resetMemory() { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface SecureAgent {
    /** Access mode — AUTHENTICATED (default) or PUBLIC. */
    AccessMode mode() default AccessMode.AUTHENTICATED;

    /**
     * Required roles. Caller must have AT LEAST ONE of these roles.
     * Empty = any authenticated caller is allowed.
     */
    String[] roles() default {};

    /** Whether to write an audit log entry for every call. */
    boolean auditLog() default true;

    /** Custom message shown when access is denied. */
    String denyMessage() default "Access denied — insufficient permissions";

    /**
     * Whether to include the agent output in the audit log.
     * Set false for sensitive outputs (PII, financial data).
     */
    boolean logOutput() default false;

    /**
     * Optional rate limit — max calls per minute per identity.
     * 0 = unlimited.
     */
    int rateLimit() default 0;
}