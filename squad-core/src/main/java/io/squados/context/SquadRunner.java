package io.squados.context;

import io.squados.annotation.SquadApplication;
import io.squados.config.SquadConfig;
import io.squados.config.SquadConfigParser;
import io.squados.llm.LlmPort;
import io.squados.llm.MockLlmPort;

/**
 * Entry point for a SquadOS application.
 *
 * Mirrors Spring Boot's SpringApplication.run() — call it from main(),
 * get back a ready SquadContext.
 *
 * Named SquadRunner (not SquadApplication) to avoid a simple-name
 * collision with the @SquadApplication annotation in io.squados.annotation.
 *
 * Usage:
 * <pre>
 * {@literal @}SquadApplication
 * public class Main {
 *     public static void main(String[] args) {
 *         SquadContext ctx = SquadRunner.run(Main.class, args);
 *         System.out.println(ctx.submit("Plan the mission").content());
 *     }
 * }
 * </pre>
 */
public class SquadRunner {

    private SquadRunner() {} // Static utility class

    /**
     * Boot a SquadOS application from a class annotated with @SquadApplication.
     * Loads squad.yml from the classpath, resolves the LlmPort, boots the context.
     */
    public static SquadContext run(Class<?> mainClass, String... args) {
        validateMainClass(mainClass);
        SquadConfig config = SquadConfigParser.load();
        LlmPort     llm    = resolveLlmPort();
        SquadContext ctx    = new SquadContext(config, llm);
        ctx.boot();
        return ctx;
    }

    /**
     * Boot variant accepting a pre-built LlmPort.
     * Used in tests and when the caller manages LLM wiring explicitly.
     */
    public static SquadContext run(Class<?> mainClass, LlmPort llm, String... args) {
        validateMainClass(mainClass);
        SquadConfig config = SquadConfigParser.load();
        SquadContext ctx   = new SquadContext(config, llm);
        ctx.boot();
        return ctx;
    }

    /**
     * Boot variant accepting both config and llm directly — for unit tests.
     * Skips squad.yml loading and @SquadApplication validation.
     */
    public static SquadContext run(SquadConfig config, LlmPort llm) {
        SquadContext ctx = new SquadContext(config, llm);
        ctx.boot();
        return ctx;
    }

    // ── Internals ─────────────────────────────────────────────────────

    private static void validateMainClass(Class<?> cls) {
        if (!cls.isAnnotationPresent(SquadApplication.class)) {
            throw new IllegalArgumentException(
                "[SquadOS] " + cls.getSimpleName()
                + " is not annotated with @SquadApplication. "
                + "Add @SquadApplication to your main class."
            );
        }
    }

    private static LlmPort resolveLlmPort() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");

        if (apiKey != null && !apiKey.isBlank()) {
            System.out.println("[SquadOS] ANTHROPIC_API_KEY detected.");
            System.out.println("[SquadOS] NOTE: Wire SpringAiLlmAdapter in Phase 2 for real LLM calls.");
            System.out.println("[SquadOS] Running with MockLlmPort for Phase 1 validation.");
        }

        MockLlmPort mock = new MockLlmPort();
        mock.setDefaultResponse(
            "SquadOS Phase 1 — framework booted successfully. "
            + "Wire SpringAiLlmAdapter in Phase 2 for real LLM responses."
        );
        return mock;
    }
}
