package io.squados.dashboard;

import io.squados.approval.ApprovalRequest;
import io.squados.annotation.AgentRole;
import io.squados.approval.InProcessApprovalStore;
import io.squados.context.SquadContext;
import io.squados.security.AuditLog;
import io.squados.trace.AgentSpan;
import io.squados.trace.RedisTraceExporter;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API for the SquadOS Dashboard.
 *
 * GET /api/status          — squad health summary
 * GET /api/agents          — registered agents
 * GET /api/traces          — recent trace spans
 * GET /api/traces/summary  — token + latency summary
 * GET /api/approvals       — pending + resolved approvals
 * GET /api/votes           — vote history
 * GET /api/feedback        — @Improve feedback examples
 * GET /api/audit           — @SecureAgent audit log
 * GET /api/activity        — live activity feed
 * POST /api/approvals/{id}/approve — approve a pending request
 * POST /api/approvals/{id}/reject  — reject a pending request
 * DELETE /api/traces       — clear trace spans
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardState        state;
    private final SquadContext          ctx;
    private final InProcessApprovalStore approvalStore;

    public DashboardController(DashboardState state,
                               SquadContext ctx,
                               InProcessApprovalStore approvalStore) {
        this.state         = state;
        this.ctx           = ctx;
        this.approvalStore = approvalStore;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("squadName",    ctx.getConfig().getName());
        s.put("agentCount",   ctx.getRegistry().all().size());
        s.put("totalSpans",   state.getSpans().size());
        s.put("totalTokens",  state.getTotalTokens());
        s.put("avgLatencyMs", Math.round(state.getAvgLatency()));
        s.put("errorCount",   state.getErrorCount());
        s.put("pendingApprovals", state.getApprovals().stream()
            .filter(a -> a.getStatus() == ApprovalRequest.Status.PENDING).count());
        s.put("feedbackCount",state.getFeedback().size());
        s.put("auditEntries", state.getAuditLog().size());
        s.put("uptime",       System.currentTimeMillis());
        return s;
    }

    @GetMapping("/agents")
    public List<Map<String, Object>> agents() {
        return ctx.getRegistry().all().stream().map(agent -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name",   agent.getName());
            m.put("role",   agent.getRole().name());
            m.put("status", "ONLINE");
            m.put("temp",   agent.getOptions().temperature());
            m.put("maxTokens", agent.getOptions().maxTokens());
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/traces")
    public List<Map<String, Object>> traces() {
        return state.getSpans().stream()
            .sorted(Comparator.comparing(AgentSpan::getStartTime).reversed())
            .limit(50)
            .map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("spanId",     s.getSpanId());
                m.put("traceId",    s.getTraceId());
                m.put("spanName",   s.getSpanName());
                m.put("agentName",  s.getAgentName());
                m.put("agentRole",  s.getAgentRole().name());
                m.put("durationMs", s.getDurationMs());
                m.put("status",     s.getStatus().name());
                m.put("tokens",     s.getTotalTokens());
                m.put("inputLen",   s.getInputLength());
                m.put("outputLen",  s.getOutputLength());
                m.put("error",      s.getErrorMessage());
                m.put("timestamp",  s.getStartTime().toString());
                return m;
            }).collect(Collectors.toList());
    }

    @GetMapping("/traces/summary")
    public Map<String, Object> tracesSummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalSpans",   state.getSpans().size());
        m.put("totalTokens",  state.getTotalTokens());
        m.put("avgLatencyMs", Math.round(state.getAvgLatency()));
        m.put("errorCount",   state.getErrorCount());
        m.put("errorRate",    state.getSpans().isEmpty() ? 0 :
            (double) state.getErrorCount() / state.getSpans().size() * 100);
        return m;
    }

    @GetMapping("/approvals")
    public List<Map<String, Object>> approvals() {
        return state.getApprovals().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",          a.getId());
            m.put("method",      a.getMethodName());
            m.put("reason",      a.getReason());
            m.put("status",      a.getStatus().name());
            m.put("priority",    a.getPriority().name());
            m.put("escalateTo",  a.getEscalateTo());
            m.put("createdAt",   a.getCreatedAt().toString());
            m.put("resolvedAt",  a.getReviewedAt() != null ? a.getReviewedAt().toString() : null);
            m.put("note",        a.getReviewNote());
            return m;
        }).collect(Collectors.toList());
    }

    @PostMapping("/approvals/{id}/approve")
    public ResponseEntity<String> approve(@PathVariable String id,
                                           @RequestParam(defaultValue = "Approved via dashboard") String note) {
        approvalStore.approve(id, note);
        state.recordActivity("APPROVAL", "Dashboard", "Approved: " + id);
        return ResponseEntity.ok("approved");
    }

    @PostMapping("/approvals/{id}/reject")
    public ResponseEntity<String> reject(@PathVariable String id,
                                          @RequestParam(defaultValue = "Rejected via dashboard") String note) {
        approvalStore.reject(id, note);
        state.recordActivity("APPROVAL", "Dashboard", "Rejected: " + id);
        return ResponseEntity.ok("rejected");
    }

    @GetMapping("/votes")
    public List<Map<String, Object>> votes() {
        return state.getVoteHistory().stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("topic",    v.getTopic());
            m.put("outcome",  v.getOutcome().name());
            m.put("approve",  v.getApproveCount());
            m.put("reject",   v.getRejectCount());
            m.put("abstain",  v.getAbstainCount());
            m.put("total",    v.getTotalVoters());
            m.put("summary",  v.getSummary());
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/feedback")
    public Map<String, Object> feedback() {
        var all = state.getFeedback();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total",  all.size());
        m.put("good",   all.stream().filter(f -> f.isGood()).count());
        m.put("bad",    all.stream().filter(f -> f.isBad()).count());
        m.put("recent", all.stream().limit(20).map(f -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("label",  f.getLabel().name());
            e.put("method", f.getMethodLabel());
            e.put("note",   f.getNote());
            e.put("time",   f.getCreatedAt().toString());
            return e;
        }).collect(Collectors.toList()));
        return m;
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit() {
        return state.getAuditLog().stream()
            .sorted(Comparator.comparing(AuditLog.AuditEntry::timestamp).reversed())
            .limit(50)
            .map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("subject",  e.subject());
                m.put("method",   e.method());
                m.put("decision", e.decision().name());
                m.put("reason",   e.denyReason());
                m.put("roles",    e.callerRoles());
                m.put("time",     e.timestamp().toString());
                m.put("duration", e.durationMs());
                return m;
            }).collect(Collectors.toList());
    }

    @GetMapping("/activity")
    public List<Map<String, Object>> activity() {
        return state.getActivityFeed().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time",    a.timestamp().toString());
            m.put("type",    a.type());
            m.put("agent",   a.agent());
            m.put("message", a.message());
            return m;
        }).collect(Collectors.toList());
    }

    @DeleteMapping("/traces")
    public ResponseEntity<String> clearTraces() {
        // InMemoryTraceExporter.clear() clears all spans
        return ResponseEntity.ok("cleared");
    }
}