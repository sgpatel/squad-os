package io.squados.tests;

import io.squados.annotation.*;
import io.squados.security.*;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

/**
 * Phase 19 — v3.0 @SecureAgent RBAC + JWT
 *
 * S01 — AgentIdentity.hasRole returns true for matching role
 * S02 — AgentIdentity.hasRole returns false for missing role
 * S03 — AgentIdentity.hasAnyRole true when one role matches
 * S04 — AgentIdentity.isAnonymous true for ANONYMOUS identity
 * S05 — AgentIdentity.isExpired true for expired identity
 * S06 — SecurityContext.set + current returns same identity
 * S07 — SecurityContext.clear resets to ANONYMOUS
 * S08 — JwtValidator.createTestToken + validate roundtrip
 * S09 — JwtValidator rejects expired token
 * S10 — JwtValidator rejects invalid format
 * S11 — JwtValidator rejects wrong issuer
 * S12 — SecurityGuard grants access for authenticated caller with role
 * S13 — SecurityGuard denies when anonymous (AUTHENTICATED mode)
 * S14 — SecurityGuard denies when role missing
 * S15 — SecurityGuard allows PUBLIC mode without authentication
 * S16 — SecurityGuard writes audit log on GRANTED
 * S17 — SecurityGuard writes audit log on DENIED
 */
public class SquadOsPhase19Tests {

    static class LoanAgent {
        @SecureAgent(roles = {"compliance", "risk"}, auditLog = true)
        public String underwriteLoan(String input) { return "approved"; }

        @SecureAgent(roles = {"admin"}, denyMessage = "Admins only")
        public String adminAction() { return "done"; }

        @SecureAgent(mode = AccessMode.PUBLIC, auditLog = true)
        public String getPublicRates() { return "rate: 5.5%"; }

        public String noSecurity() { return "open"; }
    }

    Method m(String name) {
        try {
            return LoanAgent.class.getDeclaredMethod(name)
                != null ? LoanAgent.class.getDeclaredMethod(name) : null;
        } catch (NoSuchMethodException e) {
            try { return LoanAgent.class.getDeclaredMethod(name, String.class); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        }
    }

    SecurityGuard guard() { return new SecurityGuard(new AuditLog()); }

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase19Tests();
        String[] tests = {
            "S01_hasRoleTrue",
            "S02_hasRoleFalse",
            "S03_hasAnyRoleTrue",
            "S04_isAnonymousTrue",
            "S05_isExpiredTrue",
            "S06_securityContextSetAndGet",
            "S07_securityContextClear",
            "S08_jwtValidatorRoundtrip",
            "S09_jwtValidatorRejectsExpired",
            "S10_jwtValidatorRejectsInvalidFormat",
            "S11_jwtValidatorRejectsWrongIssuer",
            "S12_guardGrantsWithRole",
            "S13_guardDeniesAnonymous",
            "S14_guardDeniesWrongRole",
            "S15_guardAllowsPublicMode",
            "S16_guardAuditLogGranted",
            "S17_guardAuditLogDenied",
        };
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 19 — v3.0 @SecureAgent       ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");
        for (String name : tests) {
            try {
                t.getClass().getDeclaredMethod(name).invoke(t);
                System.out.printf("  ✓ %s%n", name); passed++;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable c = e.getCause();
                System.out.printf("  ✗ %s%n    → %s: %s%n",
                    name, c.getClass().getSimpleName(), c.getMessage()); failed++;
            } catch (Exception e) {
                System.out.printf("  ✗ %s%n    → %s%n", name, e.getMessage()); failed++;
            } finally {
                SecurityContext.clear();
            }
        }
        System.out.printf("%n  Results: %d passed, %d failed%n", passed, failed);
        if (failed > 0) { System.out.println("\n  PHASE 19 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 19 GATE: ALL TESTS PASSED ✓");
               System.out.println("  v3.0 @SecureAgent RBAC operational.\n"); }
    }

    void S01_hasRoleTrue() {
        AgentIdentity id = AgentIdentity.of("alice", "compliance", "risk");
        assertTrue(id.hasRole("compliance"), "compliance role present");
        assertTrue(id.hasRole("risk"), "risk role present");
    }

    void S02_hasRoleFalse() {
        AgentIdentity id = AgentIdentity.of("bob", "marketing");
        assertFalse(id.hasRole("compliance"), "no compliance role");
    }

    void S03_hasAnyRoleTrue() {
        AgentIdentity id = AgentIdentity.of("carol", "risk");
        assertTrue(id.hasAnyRole("compliance", "risk"), "has risk — matches any");
        assertFalse(id.hasAnyRole("admin", "superuser"), "no admin or superuser");
    }

    void S04_isAnonymousTrue() {
        assertTrue(AgentIdentity.ANONYMOUS.isAnonymous(), "ANONYMOUS.isAnonymous");
        assertFalse(AgentIdentity.of("alice").isAnonymous(), "real identity not anonymous");
    }

    void S05_isExpiredTrue() {
        AgentIdentity expired = new AgentIdentity(
            "old", List.of(), "test", Instant.EPOCH, "");
        assertTrue(expired.isExpired(), "past expiry = expired");
        assertFalse(AgentIdentity.of("fresh").isExpired(), "MAX expiry = not expired");
    }

    void S06_securityContextSetAndGet() {
        AgentIdentity id = AgentIdentity.of("alice", "compliance");
        SecurityContext.set(id);
        assertEquals(id, SecurityContext.current(), "same identity retrieved");
        assertTrue(SecurityContext.isAuthenticated(), "isAuthenticated true");
    }

    void S07_securityContextClear() {
        SecurityContext.set(AgentIdentity.of("alice"));
        SecurityContext.clear();
        assertTrue(SecurityContext.current().isAnonymous(), "cleared = anonymous");
        assertFalse(SecurityContext.isAuthenticated(), "isAuthenticated false after clear");
    }

    void S08_jwtValidatorRoundtrip() {
        long expiry = Instant.now().plusSeconds(3600).getEpochSecond();
        String token = JwtValidator.createTestToken("alice", "squados", expiry,
            "compliance", "risk");
        JwtValidator validator = new JwtValidator("squados");
        AgentIdentity id = validator.validate(token);
        assertEquals("alice", id.getSubject(), "subject correct");
        assertTrue(id.hasRole("compliance"), "compliance role");
        assertTrue(id.hasRole("risk"), "risk role");
        assertEquals("squados", id.getIssuer(), "issuer correct");
    }

    void S09_jwtValidatorRejectsExpired() {
        long past = Instant.now().minusSeconds(3600).getEpochSecond();
        String token = JwtValidator.createTestToken("alice", "squados", past, "risk");
        JwtValidator validator = new JwtValidator();
        try {
            validator.validate(token);
            throw new AssertionError("should have thrown");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("expired"), "expired message");
        }
    }

    void S10_jwtValidatorRejectsInvalidFormat() {
        JwtValidator validator = new JwtValidator();
        try {
            validator.validate("not.a.valid.jwt.token.with.too.many.parts");
            throw new AssertionError("should have thrown");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("format") ||
                       e.getMessage().contains("Invalid"), "format error message");
        }
    }

    void S11_jwtValidatorRejectsWrongIssuer() {
        long expiry = Instant.now().plusSeconds(3600).getEpochSecond();
        String token = JwtValidator.createTestToken("alice", "evil-issuer", expiry, "risk");
        JwtValidator validator = new JwtValidator("squados");
        try {
            validator.validate(token);
            throw new AssertionError("should have thrown");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("issuer"), "issuer mismatch message");
        }
    }

    void S12_guardGrantsWithRole() {
        SecurityContext.set(AgentIdentity.of("alice", "compliance"));
        SecurityGuard guard = guard();
        Method meth = m("underwriteLoan");
        String result = guard.execute(meth, () -> "approved");
        assertEquals("approved", result, "result returned on GRANTED");
    }

    void S13_guardDeniesAnonymous() {
        SecurityContext.clear(); // anonymous
        SecurityGuard guard = guard();
        Method meth = m("underwriteLoan");
        try {
            guard.execute(meth, () -> "approved");
            throw new AssertionError("should have thrown");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("Authentication"), "auth required message");
        }
    }

    void S14_guardDeniesWrongRole() {
        SecurityContext.set(AgentIdentity.of("bob", "marketing")); // wrong role
        SecurityGuard guard = guard();
        Method meth = m("underwriteLoan");
        try {
            guard.execute(meth, () -> "approved");
            throw new AssertionError("should have thrown");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("denied") ||
                       e.getMessage().contains("Missing"), "role denied message");
        }
    }

    void S15_guardAllowsPublicMode() {
        SecurityContext.clear(); // anonymous — but method is PUBLIC
        SecurityGuard guard = guard();
        Method meth = m("getPublicRates");
        String result = guard.execute(meth, () -> "rate: 5.5%");
        assertEquals("rate: 5.5%", result, "public method returns result");
    }

    void S16_guardAuditLogGranted() {
        SecurityContext.set(AgentIdentity.of("alice", "compliance"));
        AuditLog log = new AuditLog();
        SecurityGuard guard = new SecurityGuard(log);
        guard.execute(m("underwriteLoan"), () -> "approved");
        assertEquals(1, log.getGranted().size(), "1 GRANTED entry");
        assertEquals(0, log.getDenied().size(),  "0 DENIED entries");
        assertEquals("alice", log.getGranted().get(0).subject(), "subject logged");
    }

    void S17_guardAuditLogDenied() {
        SecurityContext.clear(); // anonymous
        AuditLog log = new AuditLog();
        SecurityGuard guard = new SecurityGuard(log);
        try { guard.execute(m("underwriteLoan"), () -> "x"); }
        catch (SecurityException ignored) {}
        assertEquals(1, log.getDenied().size(), "1 DENIED entry");
        assertEquals(0, log.getGranted().size(), "0 GRANTED entries");
    }

    static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    static void assertTrue(boolean c, String msg) {
        if (c) return; throw new AssertionError(msg + " — expected true");
    }
    static void assertFalse(boolean c, String msg) {
        if (!c) return; throw new AssertionError(msg + " — expected false");
    }
}