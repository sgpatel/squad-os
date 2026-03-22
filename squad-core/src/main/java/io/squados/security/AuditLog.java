package io.squados.security;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Audit log for @SecureAgent calls.
 *
 * Every call to a @SecureAgent method is recorded with:
 *   - Caller identity (subject + roles)
 *   - Method called
 *   - Access decision (GRANTED / DENIED)
 *   - Timestamp and duration
 *   - Optional output snippet (if logOutput=true)
 *
 * Implementations:
 *   InMemoryAuditLog — for testing and dev
 *   (wire your own: database, Elasticsearch, SIEM)
 */
public class AuditLog {

    public enum Decision { GRANTED, DENIED }

    public record AuditEntry(
        String    entryId,
        String    subject,
        List<String> callerRoles,
        String    method,
        String[]  requiredRoles,
        Decision  decision,
        String    denyReason,
        Instant   timestamp,
        long      durationMs,
        String    outputSnippet   // null if logOutput=false
    ) {
        @Override public String toString() {
            return String.format(
                "[AuditLog] %s | %s | %s | caller=%s | roles=%s | %s",
                timestamp, decision, method, subject, callerRoles,
                decision == Decision.DENIED ? "DENIED: " + denyReason : "GRANTED");
        }
    }

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    public void record(AuditEntry entry) {
        entries.add(entry);
        System.out.println(entry);
    }

    public AuditEntry buildEntry(AgentIdentity identity,
                                  String method,
                                  String[] requiredRoles,
                                  Decision decision,
                                  String denyReason,
                                  long durationMs,
                                  String outputSnippet) {
        return new AuditEntry(
            UUID.randomUUID().toString(),
            identity.getSubject(),
            identity.getRoles(),
            method,
            requiredRoles,
            decision,
            denyReason,
            Instant.now(),
            durationMs,
            outputSnippet
        );
    }

    public List<AuditEntry> getAll()                       { return Collections.unmodifiableList(entries); }
    public int              size()                         { return entries.size(); }
    public List<AuditEntry> getBySubject(String subject)   {
        return entries.stream().filter(e -> subject.equals(e.subject())).toList();
    }
    public List<AuditEntry> getDenied()                    {
        return entries.stream().filter(e -> e.decision() == Decision.DENIED).toList();
    }
    public List<AuditEntry> getGranted()                   {
        return entries.stream().filter(e -> e.decision() == Decision.GRANTED).toList();
    }
}