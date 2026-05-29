package io.squados.dashboard.api.service;

import io.squados.dashboard.api.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

// Note: Optional removed — dashboard is read-only, no approval mutations

/**
 * Core data service for the SquadOS dashboard.
 *
 * ── Data source strategy ───────────────────────────────────────────────────
 *
 * Redis mode  (squad.redis.enabled=true):
 *   Reads real agent spans from squados:traces (LIST) and real workflow states
 *   from squados:durable:* — the same keys written by SquadOS agents.
 *   Total token count comes from squados:traces:tokens (INCRBY).
 *   Agent metrics are derived by aggregating the real spans.
 *   The simulation tick is disabled; data refreshes from Redis every 5s.
 *
 * Simulation mode (default, squad.redis.enabled=false):
 *   Seeds in-memory state with realistic synthetic data and updates it with
 *   a @Scheduled tick every 3 seconds so the UI feels live without any deps.
 *
 * Approval events in both modes:
 *   Approvals are always in-memory — POST /api/v1/approvals/register can be
 *   called by the SquadOS application (e.g., via a custom ApprovalStore hook)
 *   to push pending requests to the dashboard.
 */
@Service
public class DashboardDataService {

    private static final Logger log = Logger.getLogger(DashboardDataService.class.getName());

    private static final DateTimeFormatter HM = DateTimeFormatter
            .ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    // ── Config ────────────────────────────────────────────────────────────────

    @Value("${squad.redis.enabled:false}")
    private boolean redisEnabled;

    @Value("${squad.redis.host:localhost}")
    private String redisHost;

    @Value("${squad.redis.port:6379}")
    private int redisPort;

    @Value("${squad.redis.password:}")
    private String redisPassword;

    // ── Redis reader (null in simulation mode) ────────────────────────────────

    private RedisDataReader redisReader;
    private boolean         redisAvailable = false;

    // ── Agent Registry ────────────────────────────────────────────────────────

    private final List<AgentState> agents = new CopyOnWriteArrayList<>();

    // ── Trace Ring-Buffer ─────────────────────────────────────────────────────

    private final Deque<TraceSpan> traceBuffer = new ArrayDeque<>(500);

    // ── Security Events ───────────────────────────────────────────────────────

    private final Deque<SecurityEvent> securityEvents = new ArrayDeque<>(200);

    // ── Approval Queue ────────────────────────────────────────────────────────

    private final List<ApprovalItem> approvals = new CopyOnWriteArrayList<>();

    // ── Workflow Cache ────────────────────────────────────────────────────────

    private volatile List<WorkflowItem> workflowCache = new CopyOnWriteArrayList<>();

    // ── Time-Series Buckets (last 60 minutes) ─────────────────────────────────

    private final Deque<long[]> callBuckets    = new ArrayDeque<>(60);
    private final Deque<long[]> tokenBuckets   = new ArrayDeque<>(60);
    private final Deque<long[]> latencyBuckets = new ArrayDeque<>(60);

    // ── Totals ────────────────────────────────────────────────────────────────

    private final AtomicLong totalCalls    = new AtomicLong();
    private final AtomicLong totalSuccess  = new AtomicLong();
    private final AtomicLong totalErrors   = new AtomicLong();
    private final AtomicLong totalTokens   = new AtomicLong();
    private final AtomicLong totalPrompt   = new AtomicLong();
    private final AtomicLong totalComplete = new AtomicLong();
    private final long       startTime     = System.currentTimeMillis();

    private final ActivityEventService eventService;

    public DashboardDataService(ActivityEventService eventService) {
        this.eventService = eventService;
    }

    @PostConstruct
    public void init() {
        if (redisEnabled) {
            tryConnectRedis();
        }
        if (!redisAvailable) {
            log.info("[Dashboard] Redis not available — starting in simulation mode");
            seedSimulation();
        } else {
            log.info("[Dashboard] Redis connected — loading real SquadOS data");
            seedApprovals(ThreadLocalRandom.current()); // approvals not in Redis, seed demo ones
            refreshFromRedis();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (redisReader != null) {
            try { redisReader.close(); } catch (Exception ignored) {}
        }
    }

    // ── Redis connection ──────────────────────────────────────────────────────

    private void tryConnectRedis() {
        try {
            String pass = (redisPassword == null || redisPassword.isBlank()) ? null : redisPassword;
            redisReader = new RedisDataReader(redisHost, redisPort, pass);
            redisAvailable = redisReader.isAvailable();
            if (!redisAvailable) {
                log.warning("[Dashboard] Redis ping failed — falling back to simulation");
                redisReader.close();
                redisReader = null;
            }
        } catch (Exception e) {
            log.warning("[Dashboard] Cannot connect to Redis (" + e.getMessage() + ") — simulation mode");
            redisAvailable = false;
            redisReader = null;
        }
    }

    // ── Redis refresh (every 5 seconds when Redis mode is active) ─────────────

    @Scheduled(fixedDelay = 5000)
    public void refreshIfRedis() {
        if (!redisAvailable || redisReader == null) return;
        refreshFromRedis();
    }

    private void refreshFromRedis() {
        try {
            // 1. Load traces and derive agent metrics from them
            List<TraceSpan> spans = redisReader.getTraces(500);
            rebuildFromSpans(spans);

            // 2. Total token count (authoritative from Redis INCRBY)
            long redisTok = redisReader.totalTokens();
            if (redisTok > 0) totalTokens.set(redisTok);

            // 3. Durable workflows
            List<WorkflowItem> workflows = redisReader.getWorkflows();
            workflowCache = new CopyOnWriteArrayList<>(workflows);

            // 4. Publish SSE heartbeat
            eventService.publishSystem("Redis sync — " + spans.size() + " spans, "
                + workflows.size() + " workflows");

        } catch (Exception e) {
            log.warning("[Dashboard] Redis refresh failed: " + e.getMessage());
        }
    }

    /**
     * Rebuild all agent state and time-series from a fresh set of real spans.
     * Called on every Redis refresh cycle.
     */
    private synchronized void rebuildFromSpans(List<TraceSpan> spans) {
        // Replace trace buffer
        traceBuffer.clear();
        for (TraceSpan s : spans) {
            if (traceBuffer.size() >= 500) break;
            traceBuffer.addLast(s);
        }

        // Aggregate per-agent metrics
        Map<String, AgentState> byAgent = new LinkedHashMap<>();
        long calls = 0, success = 0, errors = 0, prompt = 0, complete = 0;

        for (TraceSpan span : spans) {
            AgentState a = byAgent.computeIfAbsent(span.agentName(), name ->
                new AgentState(name, span.role(), "SquadOS agent", "ACTIVE",
                    0, 0, 0, 0, 0, 0.0, null, "@Traced"));
            boolean ok = "OK".equals(span.status());
            a.recordCall(ok, span.durationMs(), span.totalTokens(),
                span.promptTokens(), span.completionTokens());

            calls++;
            if (ok) success++; else errors++;
            prompt   += span.promptTokens();
            complete += span.completionTokens();
        }

        totalCalls  .set(calls);
        totalSuccess.set(success);
        totalErrors .set(errors);
        totalPrompt .set(prompt);
        totalComplete.set(complete);

        // Rebuild agent list (keep ordering stable)
        agents.clear();
        agents.addAll(byAgent.values());

        // Rebuild time-series from span timestamps (last 60 min, 1-min buckets)
        rebuildTimeSeries(spans);
    }

    private void rebuildTimeSeries(List<TraceSpan> spans) {
        long now = System.currentTimeMillis();
        long[] callArr    = new long[60];
        long[] tokenArr   = new long[60];
        long[] latencyArr = new long[60];
        long[] latCnt     = new long[60];

        for (TraceSpan s : spans) {
            long ts = s.startTime().toEpochMilli();
            long age = now - ts;
            if (age > 60 * 60_000L) continue; // older than 60 min
            int bucket = (int)(age / 60_000L);
            if (bucket < 0 || bucket >= 60) continue;
            int idx = 59 - bucket;
            callArr[idx]++;
            tokenArr[idx]   += s.totalTokens();
            latencyArr[idx] += s.durationMs();
            latCnt[idx]++;
        }

        callBuckets.clear(); tokenBuckets.clear(); latencyBuckets.clear();
        for (int i = 0; i < 60; i++) {
            long ts = now - (59 - i) * 60_000L;
            callBuckets  .addLast(new long[]{ts, callArr[i]});
            tokenBuckets .addLast(new long[]{ts, tokenArr[i]});
            latencyBuckets.addLast(new long[]{ts, latCnt[i] > 0 ? latencyArr[i] / latCnt[i] : 0});
        }
    }

    // ── Simulation tick (only active when Redis is NOT available) ─────────────

    @Scheduled(fixedDelay = 3000)
    public void simulateTick() {
        if (redisAvailable) return; // Redis mode — no simulation
        if (agents.isEmpty()) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        AgentState agent = agents.get(rng.nextInt(agents.size()));
        boolean success  = rng.nextDouble() > 0.08;
        long    latency  = rng.nextLong(50, 1500);
        int     tokens   = rng.nextInt(100, 900);
        int     prompt   = tokens / 2;
        int     complete = tokens - prompt;

        agent.recordCall(success, latency, tokens, prompt, complete);
        totalCalls.incrementAndGet();
        if (success) totalSuccess.incrementAndGet(); else totalErrors.incrementAndGet();
        totalTokens  .addAndGet(tokens);
        totalPrompt  .addAndGet(prompt);
        totalComplete.addAndGet(complete);

        TraceSpan span = buildSimSpan(agent, success, latency, prompt, complete, rng);
        synchronized (this) {
            if (traceBuffer.size() >= 500) traceBuffer.pollFirst();
            traceBuffer.addLast(span);
        }

        eventService.publishAgentCall(agent.name, agent.role, success ? "success" : "error",
            latency, tokens, success ? "Completed successfully" : "LLM call failed after retry");

        if (rng.nextDouble() < 0.10) {
            SecurityEvent sec = buildSimSecurityEvent(rng);
            addSecurityEvent(sec);
            eventService.publishEvent("SECURITY", sec.agentName(), null, sec.message(), sec.severity().toLowerCase());
        }

        if (rng.nextDouble() < 0.05) {
            approvals.add(buildSimApproval(rng, true));
        }

        updateBuckets(tokens, latency);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public List<AgentSummary> getAgentSummaries() {
        List<AgentSummary> result = new ArrayList<>();
        for (AgentState a : agents) {
            long calls = a.totalCalls;
            long errors = a.errorCalls;
            double successRate = calls == 0 ? 1.0 : (double)(calls - errors) / calls;
            result.add(new AgentSummary(
                a.name, a.role, a.description, a.status,
                calls, calls - errors, errors,
                a.avgLatencyMs,
                a.totalTokens, a.promptTokens, a.completionTokens,
                successRate,
                a.lastCallAt != null ? a.lastCallAt.toString() : null,
                a.annotations
            ));
        }
        return result;
    }

    public synchronized List<TraceSpan> getTraces(int limit) {
        List<TraceSpan> list = new ArrayList<>(traceBuffer);
        Collections.reverse(list);
        return list.subList(0, Math.min(limit, list.size()));
    }

    public synchronized MetricsSummary getMetrics() {
        long totalC  = totalCalls.get();
        long totalS  = totalSuccess.get();
        long totalE  = totalErrors.get();
        double rate  = totalC == 0 ? 1.0 : (double) totalS / totalC;

        List<MetricsSummary.AgentMetricEntry> perAgent = new ArrayList<>();
        for (AgentState a : agents) {
            perAgent.add(new MetricsSummary.AgentMetricEntry(
                a.name, a.role, a.totalCalls, a.errorCalls, a.totalTokens, a.avgLatencyMs));
        }
        perAgent.sort(Comparator.comparingLong(MetricsSummary.AgentMetricEntry::calls).reversed());

        List<MetricsSummary.TimeSeriesPoint> callsTs   = new ArrayList<>();
        List<MetricsSummary.TimeSeriesPoint> tokensTs  = new ArrayList<>();
        List<MetricsSummary.TimeSeriesPoint> latencyTs = new ArrayList<>();
        Iterator<long[]> ci = callBuckets.iterator();
        Iterator<long[]> ti = tokenBuckets.iterator();
        Iterator<long[]> li = latencyBuckets.iterator();
        while (ci.hasNext()) {
            long[] c = ci.next(), t = ti.next(), l = li.next();
            String label = HM.format(Instant.ofEpochMilli(c[0]));
            callsTs  .add(new MetricsSummary.TimeSeriesPoint(label, c[1]));
            tokensTs .add(new MetricsSummary.TimeSeriesPoint(label, t[1]));
            latencyTs.add(new MetricsSummary.TimeSeriesPoint(label, l[1]));
        }

        Map<String, Long> errorsByType = new LinkedHashMap<>();
        errorsByType.put("RetryExhausted",    totalE * 30 / 100);
        errorsByType.put("RateLimitExceeded", totalE * 25 / 100);
        errorsByType.put("GuardrailBlocked",  totalE * 20 / 100);
        errorsByType.put("Timeout",           totalE * 15 / 100);
        errorsByType.put("Other",             totalE * 10 / 100);

        List<Long> latencies = new ArrayList<>();
        for (long[] b : latencyBuckets) latencies.add(b[1]);
        Collections.sort(latencies);
        double p95 = latencies.isEmpty() ? 0 : latencies.get((int)(latencies.size() * 0.95));
        double p99 = latencies.isEmpty() ? 0 : latencies.get((int)(latencies.size() * 0.99));
        double avg = latencies.stream().mapToLong(x -> x).average().orElse(0);

        return new MetricsSummary(
            totalC, totalS, totalE, rate,
            totalTokens.get(), totalPrompt.get(), totalComplete.get(),
            avg, p95, p99,
            perAgent, callsTs, tokensTs, latencyTs,
            errorsByType, 0, (int)(totalE / 50)
        );
    }

    public List<ApprovalItem> getApprovals(String status) {
        if (status == null || status.isBlank()) return Collections.unmodifiableList(approvals);
        return approvals.stream().filter(a -> a.status().equalsIgnoreCase(status)).toList();
    }

    public List<WorkflowItem> getWorkflows(String state) {
        List<WorkflowItem> source = redisAvailable ? workflowCache : new ArrayList<>(workflowCache);
        if (state == null || state.isBlank()) return Collections.unmodifiableList(source);
        return source.stream().filter(w -> w.state().equalsIgnoreCase(state)).toList();
    }

    public synchronized List<SecurityEvent> getSecurityEvents(int limit) {
        List<SecurityEvent> list = new ArrayList<>(securityEvents);
        Collections.reverse(list);
        return list.subList(0, Math.min(limit, list.size()));
    }

    public SystemHealth getSystemHealth() {
        Runtime rt = Runtime.getRuntime();
        long heapUsed = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long heapMax  = rt.maxMemory() / (1024 * 1024);
        Map<String, String> components = new LinkedHashMap<>();
        components.put("redis",    redisAvailable ? "UP" : "DOWN");
        components.put("tracing",  "UP");
        components.put("metrics",  "UP");
        components.put("mode",     redisAvailable ? "REDIS" : "SIMULATION");
        return new SystemHealth(
            "UP", Instant.now(),
            System.currentTimeMillis() - startTime,
            (int) agents.stream().filter(a -> "ACTIVE".equals(a.status)).count(),
            agents.size(),
            eventService.subscriberCount(),
            heapUsed, heapMax,
            heapMax == 0 ? 0.0 : (double) heapUsed / heapMax * 100,
            Thread.activeCount(),
            components
        );
    }

    public boolean isRedisMode() { return redisAvailable; }

    // ── Simulation seeding ────────────────────────────────────────────────────

    private static final String[] AGENT_NAMES = {
        "GatewayAgent","RiskAnalyst","BehaviourAgent","ComplianceAgent",
        "UnderwriterAgent","SentinelAI","MonitorAgent","AuditAgent",
        "FraudDetector","PolicyEngine"
    };
    private static final String[] ROLES = {
        "gateway","risk-analyst","behaviour","compliance","underwriter",
        "sentinel","monitor","auditor","fraud-detector","policy"
    };
    private static final String[] ANNOTATIONS = {
        "@Traced,@Retry","@Guardrails,@RateLimit,@Traced",
        "@DurableAgent,@Checkpoint,@Traced","@Pipeline,@Streaming,@Traced",
        "@Cache,@Observe,@Retry","@SecureAgent,@Traced,@AwaitApproval",
        "@AgentPool,@Observe,@Timeout","@Eval,@Improve,@Traced"
    };
    private static final String[] SEC_TYPES = {
        "AUTH_FAILURE","GUARDRAIL_BLOCK","RATE_LIMIT",
        "INJECTION_DETECTED","PII_REDACTED","ACCESS_DENIED"
    };
    private static final String[] SEC_SEVERITIES = {"LOW","MEDIUM","HIGH","CRITICAL"};

    private void seedSimulation() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < AGENT_NAMES.length; i++) {
            long calls  = rng.nextLong(200, 5000);
            long errors = rng.nextLong(0, calls / 10 + 1);
            long tokens = calls * rng.nextLong(150, 800);
            agents.add(new AgentState(
                AGENT_NAMES[i], ROLES[i % ROLES.length],
                "Handles " + ROLES[i % ROLES.length] + " tasks", "ACTIVE",
                calls, errors, tokens, tokens / 2, tokens - tokens / 2,
                rng.nextDouble(50, 1200),
                Instant.now().minusSeconds(rng.nextLong(1, 300)),
                ANNOTATIONS[i % ANNOTATIONS.length]
            ));
        }
        for (int i = 0; i < 120; i++) addTrace(buildSimTrace(rng));
        for (int i = 0; i < 40; i++) addSecurityEvent(buildSimSecurityEvent(rng));
        seedApprovals(rng);
        seedWorkflows(rng);
        seedTimeSeries(rng);

        totalCalls   .set(agents.stream().mapToLong(a -> a.totalCalls).sum());
        totalSuccess .set(agents.stream().mapToLong(a -> a.totalCalls - a.errorCalls).sum());
        totalErrors  .set(agents.stream().mapToLong(a -> a.errorCalls).sum());
        totalTokens  .set(agents.stream().mapToLong(a -> a.totalTokens).sum());
        totalPrompt  .set(agents.stream().mapToLong(a -> a.promptTokens).sum());
        totalComplete.set(agents.stream().mapToLong(a -> a.completionTokens).sum());
    }

    private void seedApprovals(ThreadLocalRandom rng) {
        for (int i = 0; i < 15; i++) approvals.add(buildSimApproval(rng, i < 5));
    }

    private void seedWorkflows(ThreadLocalRandom rng) {
        String[] states = {"RUNNING","PAUSED","COMPLETED","FAILED"};
        String[] steps  = {"validate-input","fetch-context","risk-score","compliance-check","underwrite","notify"};
        for (int i = 0; i < 20; i++) {
            String agent = AGENT_NAMES[rng.nextInt(AGENT_NAMES.length)];
            String state = states[rng.nextInt(states.length)];
            int nSteps   = rng.nextInt(2, steps.length);
            List<String> done = Arrays.asList(steps).subList(0, nSteps);
            workflowCache.add(new WorkflowItem(
                "wf-" + UUID.randomUUID().toString().substring(0, 8),
                agent, state,
                Instant.now().minusSeconds(rng.nextLong(60, 86400)),
                Instant.now().minusSeconds(rng.nextLong(0, 60)),
                rng.nextLong(500, 30000),
                nSteps, done,
                done.isEmpty() ? null : done.get(done.size()-1),
                "FAILED".equals(state) ? "Step '" + done.get(done.size()-1) + "' timed out" : null
            ));
        }
    }

    private void seedTimeSeries(ThreadLocalRandom rng) {
        long now = System.currentTimeMillis();
        for (int i = 59; i >= 0; i--) {
            long ts = now - i * 60_000L;
            callBuckets  .addLast(new long[]{ts, rng.nextLong(5, 150)});
            tokenBuckets .addLast(new long[]{ts, rng.nextLong(500, 15000)});
            latencyBuckets.addLast(new long[]{ts, rng.nextLong(80, 900)});
        }
    }

    // ── Simulation builders ───────────────────────────────────────────────────

    private TraceSpan buildSimTrace(ThreadLocalRandom rng) {
        int idx = rng.nextInt(AGENT_NAMES.length);
        AgentState a = agents.isEmpty() ? null : agents.get(idx % agents.size());
        String name  = a != null ? a.name : AGENT_NAMES[idx];
        String role  = a != null ? a.role : ROLES[idx % ROLES.length];
        boolean ok   = rng.nextDouble() > 0.08;
        long latency = rng.nextLong(50, 1800);
        int prompt   = rng.nextInt(80, 600);
        int complete = rng.nextInt(50, 400);
        return buildSimSpan(new AgentState(name, role, "", "ACTIVE", 0,0,0,0,0,0,null,""),
            ok, latency, prompt, complete, rng);
    }

    private TraceSpan buildSimSpan(AgentState agent, boolean success, long latency,
                                    int prompt, int complete, ThreadLocalRandom rng) {
        Instant end   = Instant.now().minusSeconds(rng.nextLong(0, 3600));
        Instant start = end.minusMillis(latency);
        return new TraceSpan(
            UUID.randomUUID().toString().replace("-","").substring(0,16),
            UUID.randomUUID().toString().replace("-","").substring(0,8),
            agent.name + ".chat", agent.name, agent.role,
            start, end, latency,
            success ? "OK" : "ERROR",
            success ? null : "LLM returned error after " + rng.nextInt(1,4) + " attempt(s)",
            prompt, complete, prompt + complete,
            rng.nextInt(50, 2000), rng.nextInt(50, 1000),
            Map.of("source","simulation","squad.version","3.7.0")
        );
    }

    private SecurityEvent buildSimSecurityEvent(ThreadLocalRandom rng) {
        return new SecurityEvent(
            UUID.randomUUID().toString(),
            Instant.now().minusSeconds(rng.nextLong(0, 7200)),
            SEC_TYPES[rng.nextInt(SEC_TYPES.length)],
            AGENT_NAMES[rng.nextInt(AGENT_NAMES.length)],
            "user-" + rng.nextInt(100),
            SEC_SEVERITIES[rng.nextInt(SEC_SEVERITIES.length)],
            SEC_TYPES[rng.nextInt(SEC_TYPES.length)] + " detected",
            "Detected at " + Instant.now()
        );
    }

    private ApprovalItem buildSimApproval(ThreadLocalRandom rng, boolean pending) {
        String agent  = AGENT_NAMES[rng.nextInt(AGENT_NAMES.length)];
        String role   = ROLES[rng.nextInt(ROLES.length)];
        String status = pending ? "PENDING" : (rng.nextBoolean() ? "APPROVED" : "REJECTED");
        Instant requested = Instant.now().minusSeconds(rng.nextLong(60, 3600));
        return new ApprovalItem(
            UUID.randomUUID().toString(), requested, agent, role,
            "TRANSFER_$" + rng.nextInt(1000, 50000),
            "{\"amount\":" + rng.nextInt(1000,50000) + ",\"currency\":\"USD\"}",
            status,
            "PENDING".equals(status) ? null : "admin",
            "PENDING".equals(status) ? null : requested.plusSeconds(rng.nextLong(60, 600)),
            300
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private synchronized void addTrace(TraceSpan span) {
        if (traceBuffer.size() >= 500) traceBuffer.pollFirst();
        traceBuffer.addLast(span);
    }

    private synchronized void addSecurityEvent(SecurityEvent ev) {
        if (securityEvents.size() >= 200) securityEvents.pollFirst();
        securityEvents.addLast(ev);
    }

    private synchronized void updateBuckets(int tokens, long latency) {
        long now = System.currentTimeMillis();
        long[] last = callBuckets.isEmpty() ? null : ((ArrayDeque<long[]>) callBuckets).peekLast();
        if (last == null || now - last[0] >= 60_000) {
            if (callBuckets.size() >= 60) callBuckets.pollFirst();
            if (tokenBuckets.size() >= 60) tokenBuckets.pollFirst();
            if (latencyBuckets.size() >= 60) latencyBuckets.pollFirst();
            callBuckets  .addLast(new long[]{now, 1});
            tokenBuckets .addLast(new long[]{now, tokens});
            latencyBuckets.addLast(new long[]{now, latency});
        } else {
            last[1]++;
            ((ArrayDeque<long[]>) tokenBuckets).peekLast()[1] += tokens;
            long[] lat = ((ArrayDeque<long[]>) latencyBuckets).peekLast();
            lat[1] = (lat[1] + latency) / 2;
        }
    }

    // ── Inner state ───────────────────────────────────────────────────────────

    static class AgentState {
        final String  name, role, description, annotations;
        volatile String  status;
        volatile long    totalCalls, errorCalls, totalTokens, promptTokens, completionTokens;
        volatile double  avgLatencyMs;
        volatile Instant lastCallAt;

        AgentState(String name, String role, String description, String status,
                   long calls, long errors, long tokens, long prompt, long complete,
                   double avgLatency, Instant lastCallAt, String annotations) {
            this.name = name; this.role = role; this.description = description;
            this.status = status; this.totalCalls = calls; this.errorCalls = errors;
            this.totalTokens = tokens; this.promptTokens = prompt;
            this.completionTokens = complete; this.avgLatencyMs = avgLatency;
            this.lastCallAt = lastCallAt; this.annotations = annotations;
        }

        synchronized void recordCall(boolean success, long latency, int tokens, int prompt, int complete) {
            totalCalls++;
            if (!success) errorCalls++;
            totalTokens += tokens; promptTokens += prompt; completionTokens += complete;
            avgLatencyMs = (avgLatencyMs * (totalCalls - 1) + latency) / totalCalls;
            lastCallAt = Instant.now();
            status = errorCalls > totalCalls * 0.3 ? "ERROR" : "ACTIVE";
        }
    }
}
