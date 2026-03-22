package io.squados.security;

import java.time.Instant;
import java.util.*;

/**
 * The verified identity of an agent caller.
 * Extracted from a validated JWT token.
 *
 * Contains: subject (user/service ID), roles, issuer, expiry.
 * Immutable — created by JwtValidator after successful validation.
 *
 * Usage:
 * <pre>
 * AgentIdentity identity = AgentIdentity.of("user-123", "compliance", "risk");
 * SecurityContext.set(identity);
 *
 * // In your @SecureAgent method:
 * AgentIdentity caller = SecurityContext.current();
 * System.out.println("Called by: " + caller.getSubject());
 * </pre>
 */
public class AgentIdentity {

    public static final AgentIdentity ANONYMOUS = new AgentIdentity(
        "anonymous", List.of(), "none", Instant.MAX, "");

    private final String        subject;    // user/service ID
    private final List<String>  roles;      // e.g. ["compliance", "risk"]
    private final String        issuer;     // JWT issuer
    private final Instant       expiresAt;  // token expiry
    private final String        rawToken;   // original JWT

    public AgentIdentity(String subject, List<String> roles,
                         String issuer, Instant expiresAt, String rawToken) {
        this.subject   = subject;
        this.roles     = Collections.unmodifiableList(new ArrayList<>(roles));
        this.issuer    = issuer;
        this.expiresAt = expiresAt;
        this.rawToken  = rawToken;
    }

    /** Convenience factory. */
    public static AgentIdentity of(String subject, String... roles) {
        return new AgentIdentity(subject, List.of(roles), "local", Instant.MAX, "");
    }

    public String       getSubject()  { return subject; }
    public List<String> getRoles()    { return roles; }
    public String       getIssuer()   { return issuer; }
    public Instant      getExpiresAt(){ return expiresAt; }
    public String       getRawToken() { return rawToken; }

    public boolean isAnonymous()      { return "anonymous".equals(subject); }
    public boolean isExpired()        { return Instant.now().isAfter(expiresAt); }
    public boolean hasRole(String r)  { return roles.contains(r); }
    public boolean hasAnyRole(String... required) {
        return Arrays.stream(required).anyMatch(roles::contains);
    }

    @Override
    public String toString() {
        return String.format("AgentIdentity{subject=%s, roles=%s}", subject, roles);
    }
}