package io.squados.benchmark;

import io.squados.annotation.EvalCriteria;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregate result of a full benchmark run.
 *
 * Contains per-case results, pass rate, per-criteria averages,
 * and optional comparison to a baseline version.
 */
public class BenchmarkReport {

    private final String                    agentName;
    private final String                    version;
    private final List<BenchmarkCaseResult> caseResults;
    private final float                     minScore;
    private final long                      totalMs;

    // Optional baseline for regression comparison
    private BenchmarkReport baseline;

    public BenchmarkReport(String agentName, String version,
                           List<BenchmarkCaseResult> caseResults,
                           float minScore, long totalMs) {
        this.agentName   = agentName;
        this.version     = version;
        this.caseResults = Collections.unmodifiableList(caseResults);
        this.minScore    = minScore;
        this.totalMs     = totalMs;
    }

    // ── Stats ─────────────────────────────────────────────────────────

    public int   totalCases()  { return caseResults.size(); }
    public long  passCount()   { return caseResults.stream().filter(BenchmarkCaseResult::passed).count(); }
    public long  failCount()   { return totalCases() - passCount(); }
    public float passRate()    { return totalCases() == 0 ? 0f : (float) passCount() / totalCases(); }

    public float averageScore() {
        return (float) caseResults.stream()
            .mapToDouble(r -> r.score().overall())
            .average().orElse(0.0);
    }

    /** Average score per EvalCriteria across all cases. */
    public Map<EvalCriteria, Float> perCriteriaAverage() {
        Map<EvalCriteria, List<Float>> acc = new EnumMap<>(EvalCriteria.class);
        for (BenchmarkCaseResult r : caseResults) {
            r.score().perCriteria().forEach((k, v) ->
                acc.computeIfAbsent(k, x -> new ArrayList<>()).add(v));
        }
        Map<EvalCriteria, Float> result = new EnumMap<>(EvalCriteria.class);
        acc.forEach((k, v) ->
            result.put(k, (float) v.stream().mapToDouble(Float::doubleValue).average().orElse(0)));
        return result;
    }

    public boolean meetsThreshold() { return passRate() >= minScore; }

    /** True when this version's pass rate is lower than the baseline. */
    public boolean isRegression() {
        return baseline != null && passRate() < baseline.passRate() - 0.02f; // 2% tolerance
    }

    // ── Accessors ─────────────────────────────────────────────────────

    public String                    agentName()   { return agentName; }
    public String                    version()     { return version; }
    public List<BenchmarkCaseResult> caseResults() { return caseResults; }
    public float                     minScore()    { return minScore; }
    public long                      totalMs()     { return totalMs; }

    public void setBaseline(BenchmarkReport b) { this.baseline = b; }
    public BenchmarkReport baseline()           { return baseline; }

    // ── Print ─────────────────────────────────────────────────────────

    public void print() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.printf ("║  Benchmark: %-48s  ║%n", agentName + " v" + version);
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.printf ("  Cases:      %d total, %d passed, %d failed%n",
            totalCases(), passCount(), failCount());
        System.out.printf ("  Pass rate:  %.1f%% (threshold: %.0f%%)%n",
            passRate() * 100, minScore * 100);
        System.out.printf ("  Avg score:  %.3f%n", averageScore());
        System.out.printf ("  Time:       %dms%n", totalMs);

        if (baseline != null) {
            System.out.printf ("  Baseline:   v%s pass=%.1f%% → %s%n",
                baseline.version(), baseline.passRate() * 100,
                isRegression() ? "REGRESSION ▼" : "OK ✓");
        }

        System.out.println();
        System.out.println("  Per-criteria averages:");
        perCriteriaAverage().forEach((k, v) ->
            System.out.printf("    %-20s %.3f%n", k.name() + ":", v));

        System.out.println();
        System.out.println("  Case details:");
        for (BenchmarkCaseResult r : caseResults) {
            String mark = r.passed() ? "✓" : "✗";
            System.out.printf("  [%s] score=%.2f latency=%dms%n",
                mark, r.score().overall(), r.latencyMs());
            System.out.printf("       input:    %s%n",
                r.input().substring(0, Math.min(80, r.input().length())));
            if (!r.passed()) {
                System.out.printf("       expected: %s%n",
                    r.expected().substring(0, Math.min(80, r.expected().length())));
                System.out.printf("       actual:   %s%n",
                    r.actualOutput().substring(0, Math.min(80, r.actualOutput().length())));
            }
        }
        System.out.println();
    }

    @Override
    public String toString() {
        return String.format("BenchmarkReport{agent='%s', version='%s', passRate=%.1f%%, cases=%d}",
            agentName, version, passRate() * 100, totalCases());
    }
}
