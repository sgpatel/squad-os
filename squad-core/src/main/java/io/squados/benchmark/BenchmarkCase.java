package io.squados.benchmark;

import java.util.List;

/**
 * A single test case in a benchmark dataset.
 *
 * JSON format:
 * <pre>
 * {
 *   "input":    "What is the sentiment of: 'Best product ever!'?",
 *   "expected": "POSITIVE",
 *   "tags":     ["positive", "product-review"]
 * }
 * </pre>
 *
 * CSV format (columns: input,expected,tags):
 * <pre>
 * "What is the sentiment...","POSITIVE","positive|product-review"
 * </pre>
 */
public record BenchmarkCase(
        String       input,
        String       expected,
        List<String> tags
) {
    public static BenchmarkCase of(String input, String expected) {
        return new BenchmarkCase(input, expected, List.of());
    }

    public static BenchmarkCase of(String input, String expected, List<String> tags) {
        return new BenchmarkCase(input, expected, tags);
    }
}
