package io.squados.config;

import java.util.Properties;

/**
 * Translates squad.yml llm block into Spring AI auto-configuration properties.
 *
 * Runs at application startup BEFORE Spring AI's auto-config fires,
 * so squad.yml becomes the single source of truth for LLM config —
 * the developer never needs to touch application.properties for SquadOS.
 *
 * Mapping:
 *   squad.llm.provider: anthropic   ->  spring.ai.anthropic.chat.enabled=true
 *   squad.llm.model: claude-sonnet  ->  spring.ai.anthropic.chat.options.model
 *   squad.llm.provider: openai      ->  spring.ai.openai.chat.enabled=true
 *   squad.llm.provider: ollama      ->  spring.ai.ollama.chat.enabled=true
 *
 * Usage (in your Spring Boot main class):
 * <pre>
 * {@literal @}SpringBootApplication
 * {@literal @}SquadApplication
 * public class Main {
 *     public static void main(String[] args) {
 *         // Bridge runs before Spring context starts
 *         SquadConfigBridge.applyToSystemProperties();
 *         SpringApplication.run(Main.class, args);
 *     }
 * }
 * </pre>
 */
public class SquadConfigBridge {

    private SquadConfigBridge() {}

    /**
     * Read squad.yml and inject Spring AI properties into System.properties
     * so they are picked up by Spring Boot auto-configuration.
     */
    public static void applyToSystemProperties() {
        SquadConfig config;
        try {
            config = SquadConfigParser.load();
        } catch (Exception e) {
            // squad.yml not found — let Spring AI defaults apply
            return;
        }

        Properties props = translate(config);
        props.forEach((k, v) -> {
            // Only set if not already explicitly configured by the developer
            if (System.getProperty(k.toString()) == null) {
                System.setProperty(k.toString(), v.toString());
            }
        });
    }

    /**
     * Translate a SquadConfig into Spring AI property keys.
     * Exposed for testing.
     */
    public static Properties translate(SquadConfig config) {
        Properties props = new Properties();
        String provider = config.getLlm().getProvider().toLowerCase();
        String model    = config.getLlm().getModel();

        switch (provider) {
            case "anthropic" -> {
                props.setProperty("spring.ai.anthropic.chat.enabled", "true");
                props.setProperty("spring.ai.anthropic.chat.options.model", model);
            }
            case "openai" -> {
                props.setProperty("spring.ai.openai.chat.enabled", "true");
                props.setProperty("spring.ai.openai.chat.options.model", model);
            }
            case "ollama" -> {
                props.setProperty("spring.ai.ollama.chat.enabled", "true");
                props.setProperty("spring.ai.ollama.chat.options.model", model);
            }
            case "azure-openai" -> {
                props.setProperty("spring.ai.azure.openai.chat.enabled", "true");
                props.setProperty("spring.ai.azure.openai.chat.options.model", model);
            }
            default ->
                System.out.printf("[SquadOS] Unknown LLM provider '%s' — "
                    + "configure Spring AI manually.%n", provider);
        }

        return props;
    }
}
