package io.squados.security;

/**
 * Thread-local storage for the current caller identity.
 *
 * Set by the @SecureAgent interceptor after JWT validation.
 * Read by agent methods to know who is calling them.
 *
 * Usage:
 * <pre>
 * // Before calling an agent:
 * SecurityContext.set(AgentIdentity.of("user-123", "compliance"));
 *
 * // Inside the agent method:
 * AgentIdentity caller = SecurityContext.current();
 * if (caller.hasRole("compliance")) { ... }
 *
 * // Always clear after the call:
 * SecurityContext.clear();
 * </pre>
 */
public class SecurityContext {

    private static final ThreadLocal<AgentIdentity> CONTEXT =
        ThreadLocal.withInitial(() -> AgentIdentity.ANONYMOUS);

    /** Set the identity for the current thread. */
    public static void set(AgentIdentity identity) {
        CONTEXT.set(identity);
    }

    /** Get the current identity. Returns ANONYMOUS if not set. */
    public static AgentIdentity current() {
        return CONTEXT.get();
    }

    /** Clear the identity after the call completes. */
    public static void clear() {
        CONTEXT.remove();
    }

    /** True if a non-anonymous identity is set. */
    public static boolean isAuthenticated() {
        return !CONTEXT.get().isAnonymous();
    }
}