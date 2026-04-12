package io.squados.cost;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Token-cost pricing table for LLM models.
 *
 * Prices are in USD cents per 1,000 tokens.
 * Format: {@code [promptCentsPerK, completionCentsPerK]}.
 *
 * Loaded from {@code model-pricing.properties} on the classpath if present,
 * with hard-coded defaults as fallback.
 *
 * To customise pricing, add {@code model-pricing.properties} to your project
 * resources with entries like:
 * <pre>
 *   gpt-4o.prompt=0.25
 *   gpt-4o.completion=1.00
 * </pre>
 */
public final class ModelPricingTable {

    /** promptCentsPerK, completionCentsPerK */
    private static final Map<String, double[]> PRICES = new HashMap<>();

    static {
        // Hard-coded defaults (April 2026 pricing, USD cents per 1K tokens)
        PRICES.put("gpt-4o",               new double[]{0.25,  1.00});
        PRICES.put("gpt-4o-mini",           new double[]{0.015, 0.060});
        PRICES.put("gpt-4-turbo",           new double[]{1.00,  3.00});
        PRICES.put("gpt-3.5-turbo",         new double[]{0.05,  0.15});
        PRICES.put("claude-opus-4-6",       new double[]{1.50,  7.50});
        PRICES.put("claude-sonnet-4-6",     new double[]{0.30,  1.50});
        PRICES.put("claude-haiku-4-5",      new double[]{0.025, 0.125});
        PRICES.put("claude-3-5-sonnet",     new double[]{0.30,  1.50});
        PRICES.put("claude-3-haiku",        new double[]{0.025, 0.125});
        PRICES.put("claude-3-opus",         new double[]{1.50,  7.50});
        PRICES.put("gemini-1.5-pro",        new double[]{0.35,  1.05});
        PRICES.put("gemini-1.5-flash",      new double[]{0.035, 0.105});
        PRICES.put("llama3.2",              new double[]{0.00,  0.00});   // local / free
        PRICES.put("llama3.1",              new double[]{0.00,  0.00});
        PRICES.put("mistral",               new double[]{0.00,  0.00});
        PRICES.put("mock-model",            new double[]{0.00,  0.00});

        // Override with classpath file if present
        loadFromClasspath();
    }

    private ModelPricingTable() {}

    /**
     * Calculate cost in USD cents for a single LLM call.
     *
     * @param model           Model identifier (case-insensitive prefix match).
     * @param promptTokens    Number of prompt tokens consumed.
     * @param completionTokens Number of completion tokens consumed.
     * @return Cost in USD cents; 0.0 for unknown/free models.
     */
    public static double costCents(String model, int promptTokens, int completionTokens) {
        if (model == null || model.isBlank()) return 0.0;
        double[] pricing = resolve(model.toLowerCase());
        if (pricing == null) return 0.0;
        return (promptTokens / 1000.0 * pricing[0])
             + (completionTokens / 1000.0 * pricing[1]);
    }

    /**
     * Check if a model has an entry in the pricing table.
     */
    public static boolean isKnown(String model) {
        return model != null && resolve(model.toLowerCase()) != null;
    }

    // ── Internals ─────────────────────────────────────────────────────

    private static double[] resolve(String model) {
        // Exact match first
        if (PRICES.containsKey(model)) return PRICES.get(model);
        // Prefix match (e.g. "gpt-4o-2024-11-20" matches "gpt-4o")
        for (Map.Entry<String, double[]> e : PRICES.entrySet()) {
            if (model.startsWith(e.getKey())) return e.getValue();
        }
        return null;
    }

    private static void loadFromClasspath() {
        try (InputStream is = ModelPricingTable.class.getClassLoader()
                .getResourceAsStream("model-pricing.properties")) {
            if (is == null) return;
            Properties props = new Properties();
            props.load(is);
            props.forEach((k, v) -> {
                String key = k.toString();
                if (key.endsWith(".prompt")) {
                    String model = key.substring(0, key.length() - 7);
                    PRICES.computeIfAbsent(model, m -> new double[]{0.0, 0.0})[0]
                        = Double.parseDouble(v.toString());
                } else if (key.endsWith(".completion")) {
                    String model = key.substring(0, key.length() - 11);
                    PRICES.computeIfAbsent(model, m -> new double[]{0.0, 0.0})[1]
                        = Double.parseDouble(v.toString());
                }
            });
        } catch (Exception ignored) {
            // Pricing file errors must not break startup
        }
    }
}
