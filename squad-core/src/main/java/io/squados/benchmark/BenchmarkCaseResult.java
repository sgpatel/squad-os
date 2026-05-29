package io.squados.benchmark;

import io.squados.eval.EvalScore;

/** Outcome of running a single BenchmarkCase through the agent and evaluator. */
public record BenchmarkCaseResult(
        BenchmarkCase benchmarkCase,
        String        actualOutput,
        EvalScore     score,
        boolean       passed,
        long          latencyMs
) {
    public String input()    { return benchmarkCase.input(); }
    public String expected() { return benchmarkCase.expected(); }
}
