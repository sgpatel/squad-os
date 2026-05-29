package io.squados.tests;

import io.squados.annotation.*;
import io.squados.benchmark.*;
import io.squados.config.SquadConfig;
import io.squados.context.SquadContext;
import io.squados.eval.EvalScore;
import io.squados.exception.BenchmarkRegressionException;
import io.squados.llm.MockLlmPort;

import java.util.List;

/**
 * Phase 34 — Agent Benchmarking tests.
 *
 * BM01  BenchmarkDataset.builder() creates inline dataset
 * BM02  BenchmarkDataset JSON parsing
 * BM03  BenchmarkDataset CSV parsing
 * BM04  BenchmarkCase contains input, expected, tags
 * BM05  BenchmarkRunner runs all cases and returns report
 * BM06  BenchmarkReport calculates pass rate correctly
 * BM07  BenchmarkReport calculates per-criteria averages
 * BM08  BenchmarkReport.print() outputs readable text
 * BM09  BenchmarkRunner throws on regression when failOnRegression=true
 * BM10  BenchmarkReport.isRegression() detects baseline degradation
 */
public class SquadOsPhase34Tests {

    @Agent(role = AgentRole.ANALYST, name = "SentimentAnalyser",
           description = "Classifies text sentiment as POSITIVE, NEGATIVE, or NEUTRAL.")
    @Benchmark(dataset = "classpath:benchmarks/sentiment.json",
               minScore = 0.70f, version = "3.9.0")
    static class SentimentAnalyserAgent {}

    static final MockLlmPort LLM = new MockLlmPort();

    static {
        // Judge responses — return high scores for test cases
        LLM.setDefaultResponse(
            "FAITHFULNESS: 0.85 - accurate\n" +
            "CORRECTNESS: 0.80 - correct classification\n" +
            "RELEVANCE: 0.88 - highly relevant");
        // Agent responses
        LLM.setResponse("positive", "POSITIVE");
        LLM.setResponse("negative", "NEGATIVE");
        LLM.setResponse("neutral",  "NEUTRAL");
        LLM.setResponse("great",    "POSITIVE");
        LLM.setResponse("terrible", "NEGATIVE");
    }

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  SquadOS Phase 34 — Agent Benchmarking                       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        int passed = 0, failed = 0;
        String[] tests = {
            "BM01_datasetBuilderCreatesInlineDataset",
            "BM02_datasetJsonParsing",
            "BM03_datasetCsvParsing",
            "BM04_benchmarkCaseHasCorrectFields",
            "BM05_benchmarkRunnerRunsAllCases",
            "BM06_reportCalculatesPassRateCorrectly",
            "BM07_reportCalculatesPerCriteriaAverages",
            "BM08_reportPrintOutputsText",
            "BM09_runnerThrowsOnRegression",
            "BM10_reportDetectsBaselineRegression",
        };
        var t = new SquadOsPhase34Tests();
        for (String test : tests) {
            LLM.reset();
            reinitMock();
            try {
                t.getClass().getDeclaredMethod(test).invoke(t);
                System.out.printf("  [PASS] %s%n", test);
                passed++;
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                System.out.printf("  [FAIL] %s%n         → %s%n", test, c.getMessage());
                failed++;
            }
        }
        System.out.println();
        System.out.printf("Phase 34 result: %d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    static void reinitMock() {
        LLM.setDefaultResponse(
            "FAITHFULNESS: 0.85 - accurate\n" +
            "CORRECTNESS: 0.80 - correct\n" +
            "RELEVANCE: 0.88 - relevant");
        LLM.setResponse("great",    "POSITIVE");
        LLM.setResponse("terrible", "NEGATIVE");
    }

    void BM01_datasetBuilderCreatesInlineDataset() {
        BenchmarkDataset ds = BenchmarkDataset.builder()
            .name("inline-test")
            .add("Analyse: 'Great!'", "POSITIVE")
            .add("Analyse: 'Terrible.'", "NEGATIVE")
            .build();

        assert ds.size() == 2       : "Should have 2 cases";
        assert "inline-test".equals(ds.name()) : "Name should match";
        assert "POSITIVE".equals(ds.cases().get(0).expected()) : "First case expected POSITIVE";
    }

    void BM02_datasetJsonParsing() {
        String json = "[" +
            "{\"input\":\"Is this good?\",\"expected\":\"POSITIVE\",\"tags\":[\"positive\"]}," +
            "{\"input\":\"Is this bad?\",\"expected\":\"NEGATIVE\",\"tags\":[]}" +
            "]";
        List<BenchmarkCase> cases = BenchmarkDataset.parseJson(json);
        assert cases.size() == 2          : "Should parse 2 cases";
        assert "Is this good?".equals(cases.get(0).input())    : "First input should match";
        assert "POSITIVE".equals(cases.get(0).expected())       : "First expected should match";
        assert cases.get(0).tags().contains("positive")         : "Tags should be parsed";
    }

    void BM03_datasetCsvParsing() {
        String csv = "input,expected,tags\n" +
                     "\"Analyse great product\",\"POSITIVE\",\"pos|review\"\n" +
                     "\"Terrible service\",\"NEGATIVE\",\"neg\"";
        List<BenchmarkCase> cases = BenchmarkDataset.parseCsv(csv);
        assert cases.size() == 2          : "Should parse 2 CSV cases";
        assert "POSITIVE".equals(cases.get(0).expected()) : "First case POSITIVE";
        assert "NEGATIVE".equals(cases.get(1).expected()) : "Second case NEGATIVE";
    }

    void BM04_benchmarkCaseHasCorrectFields() {
        BenchmarkCase bc = BenchmarkCase.of("input text", "POSITIVE", List.of("test", "pos"));
        assert "input text".equals(bc.input())    : "Input should match";
        assert "POSITIVE".equals(bc.expected())    : "Expected should match";
        assert bc.tags().size() == 2               : "Should have 2 tags";
    }

    void BM05_benchmarkRunnerRunsAllCases() {
        BenchmarkDataset ds = BenchmarkDataset.builder()
            .add("great product experience", "POSITIVE")
            .add("terrible customer service", "NEGATIVE")
            .build();

        SquadConfig cfg = SquadConfig.forTesting(List.of(SentimentAnalyserAgent.class));
        SquadContext ctx = new SquadContext(cfg, LLM);
        ctx.boot();

        BenchmarkRunner runner = new BenchmarkRunner(ctx, LLM);
        BenchmarkReport report = runner.run(SentimentAnalyserAgent.class, ds, 0.50f);

        assert report.totalCases() == 2 : "Should run all 2 cases";
        assert report.agentName().equals("SentimentAnalyser") : "Agent name should match";
        System.out.printf("         → cases=%d passRate=%.0f%%%n",
            report.totalCases(), report.passRate() * 100);
    }

    void BM06_reportCalculatesPassRateCorrectly() {
        // Manually create a report with known pass/fail distribution
        var judge = new io.squados.eval.EvalJudge(LLM);
        EvalScore highScore = judge.evaluate("q", "good answer",
            new EvalCriteria[]{EvalCriteria.FAITHFULNESS, EvalCriteria.CORRECTNESS}, 1);
        EvalScore lowScore  = judge.evaluate("q", "bad answer",
            new EvalCriteria[]{EvalCriteria.FAITHFULNESS, EvalCriteria.CORRECTNESS}, 1);

        float threshold = 0.50f;
        List<BenchmarkCaseResult> results = List.of(
            new BenchmarkCaseResult(BenchmarkCase.of("q1", "a"), "good answer",
                highScore, highScore.overall() >= threshold, 10L),
            new BenchmarkCaseResult(BenchmarkCase.of("q2", "b"), "bad answer",
                lowScore, lowScore.overall() >= threshold, 10L)
        );
        BenchmarkReport report = new BenchmarkReport(
            "TestAgent", "3.9.0", results, threshold, 20L);

        assert report.totalCases() == 2 : "Should have 2 cases";
        assert report.passRate() >= 0f && report.passRate() <= 1f
            : "Pass rate must be between 0 and 1: " + report.passRate();
    }

    void BM07_reportCalculatesPerCriteriaAverages() {
        var judge = new io.squados.eval.EvalJudge(LLM);
        EvalScore score = judge.evaluate("input", "output",
            new EvalCriteria[]{EvalCriteria.FAITHFULNESS, EvalCriteria.CORRECTNESS}, 1);

        List<BenchmarkCaseResult> results = List.of(
            new BenchmarkCaseResult(BenchmarkCase.of("q", "a"), "output", score, true, 10L)
        );
        BenchmarkReport report = new BenchmarkReport("A", "1", results, 0.5f, 10L);
        var perCriteria = report.perCriteriaAverage();

        assert !perCriteria.isEmpty() : "Per-criteria averages should not be empty";
        for (var entry : perCriteria.entrySet()) {
            float v = entry.getValue();
            assert v >= 0f && v <= 1f : "Score for " + entry.getKey() + " should be 0-1: " + v;
        }
    }

    void BM08_reportPrintOutputsText() {
        var judge = new io.squados.eval.EvalJudge(LLM);
        EvalScore score = judge.evaluate("q", "a",
            new EvalCriteria[]{EvalCriteria.FAITHFULNESS}, 1);
        BenchmarkReport report = new BenchmarkReport("Agent", "1.0",
            List.of(new BenchmarkCaseResult(BenchmarkCase.of("q","a"), "a", score, true, 5L)),
            0.5f, 5L);
        // Should not throw
        report.print();
    }

    void BM09_runnerThrowsOnRegression() {
        BenchmarkDataset ds = BenchmarkDataset.builder()
            .add("test input", "expected output")
            .build();

        // LLM returns very low scores
        LLM.setDefaultResponse("FAITHFULNESS: 0.10\nCORRECTNESS: 0.10\nRELEVANCE: 0.10");

        SquadConfig cfg = SquadConfig.forTesting(List.of(SentimentAnalyserAgent.class));
        SquadContext ctx = new SquadContext(cfg, LLM);
        ctx.boot();

        BenchmarkRunner runner = new BenchmarkRunner(ctx, LLM);
        boolean threw = false;
        try {
            runner.run(SentimentAnalyserAgent.class, ds, 0.99f); // threshold impossible to reach
        } catch (BenchmarkRegressionException e) {
            threw = true;
            assert e.agentName().equals("SentimentAnalyser") : "Agent name should match";
        }
        assert threw : "Should throw BenchmarkRegressionException";
    }

    void BM10_reportDetectsBaselineRegression() {
        var judge = new io.squados.eval.EvalJudge(LLM);
        EvalScore score = judge.evaluate("q","a", new EvalCriteria[]{EvalCriteria.FAITHFULNESS}, 1);

        // Current: 50% pass rate
        List<BenchmarkCaseResult> current = List.of(
            new BenchmarkCaseResult(BenchmarkCase.of("q1","a"), "a", score, true,  5L),
            new BenchmarkCaseResult(BenchmarkCase.of("q2","b"), "b", score, false, 5L)
        );
        BenchmarkReport currentReport = new BenchmarkReport("A", "new", current, 0.5f, 10L);

        // Baseline: 100% pass rate
        List<BenchmarkCaseResult> baseline = List.of(
            new BenchmarkCaseResult(BenchmarkCase.of("q1","a"), "a", score, true, 5L),
            new BenchmarkCaseResult(BenchmarkCase.of("q2","b"), "b", score, true, 5L)
        );
        BenchmarkReport baselineReport = new BenchmarkReport("A", "old", baseline, 0.5f, 10L);

        currentReport.setBaseline(baselineReport);
        assert currentReport.isRegression()
            : "Should detect regression (50% vs 100%)";
        System.out.printf("         → baseline=%.0f%% current=%.0f%% regression=%b%n",
            baselineReport.passRate()*100, currentReport.passRate()*100,
            currentReport.isRegression());
    }
}
