package io.squados.exception;

/**
 * Thrown by {@link io.squados.benchmark.BenchmarkRunner} when the pass rate
 * falls below the configured {@code minScore} threshold and
 * {@code failOnRegression=true}.
 */
public class BenchmarkRegressionException extends RuntimeException {

    private final String agentName;
    private final String version;
    private final float  actualRate;
    private final float  minScore;

    public BenchmarkRegressionException(String agentName, String version,
                                        float actualRate, float minScore) {
        super(String.format(
            "Benchmark regression for '%s' v%s: pass rate %.1f%% < threshold %.0f%%",
            agentName, version, actualRate * 100, minScore * 100));
        this.agentName  = agentName;
        this.version    = version;
        this.actualRate = actualRate;
        this.minScore   = minScore;
    }

    public String agentName()  { return agentName; }
    public String version()    { return version; }
    public float  actualRate() { return actualRate; }
    public float  minScore()   { return minScore; }
}
