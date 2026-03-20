package io.squados.config;

import io.squados.exception.SquadConfigNotFoundException;
import io.squados.exception.AgentConfigException;

import java.io.InputStream;
import java.util.*;

/**
 * Reads squad.yml from the classpath and returns a typed SquadConfig.
 *
 * Supports a minimal YAML subset without external dependencies —
 * uses a hand-rolled parser for Phase 1 so we have zero deps
 * beyond the JDK. Phase 2 will upgrade to SnakeYAML once we
 * add the Maven build.
 *
 * squad.yml format:
 * <pre>
 * squad:
 *   name: "my-squad"
 *   profile: work
 *   llm:
 *     provider: anthropic
 *     model: claude-sonnet-4-6
 *   agents:
 *     - class: com.example.OracleAgent
 *       temperature: 0.5
 *       max-tokens: 1024
 * </pre>
 */
public class SquadConfigParser {

    private static final String CONFIG_FILE = "squad.yml";

    /**
     * Load squad.yml from the classpath root.
     *
     * @throws SquadConfigNotFoundException if squad.yml is not found.
     * @throws AgentConfigException         if any agent config value is invalid.
     */
    public static SquadConfig load() {
        return load(CONFIG_FILE);
    }

    /**
     * Load a named config file — useful in tests.
     */
    public static SquadConfig load(String resourcePath) {
        InputStream stream = SquadConfigParser.class
                .getClassLoader()
                .getResourceAsStream(resourcePath);

        if (stream == null) {
            throw new SquadConfigNotFoundException(
                    "squad.yml not found. Place it in src/main/resources/squad.yml"
                            + " (looked for: " + resourcePath + ")."
            );
        }

        try {
            String yaml = new String(stream.readAllBytes());
            return parse(yaml);
        } catch (java.io.IOException e) {
            throw new SquadConfigNotFoundException(
                    "Failed to read squad.yml: " + e.getMessage()
            );
        }
    }

    /**
     * Parse a squad.yml string into a SquadConfig.
     * Exposed as package-visible for unit testing.
     *
     * State machine rules:
     *   - indent 0  : top-level key (only "squad:" supported)
     *   - indent 2  : squad-level key (name, profile, llm, agents)
     *   - indent 4  : section content (llm fields, or agent list entries with "- ")
     *   - indent 6+ : agent field continuation lines
     *
     * Context transitions happen BEFORE line content is processed,
     * so a line that ends one section and starts another is handled correctly.
     */
    public static SquadConfig parse(String yaml) {
        SquadConfig                   config       = new SquadConfig();
        SquadConfig.LlmConfig         llmConfig    = new SquadConfig.LlmConfig();
        List<SquadConfig.AgentConfig> agents       = new ArrayList<>();
        SquadConfig.AgentConfig       currentAgent = null;

        // section ∈ { "", "squad", "llm", "agents" }
        String section = "";

        for (String rawLine : yaml.split("\n")) {
            String line = rawLine.stripTrailing();
            if (line.isBlank() || line.stripLeading().startsWith("#")) continue;

            int    indent  = countLeadingSpaces(line);
            String trimmed = line.stripLeading();

            // ── indent 0 — top-level ──────────────────────────────────
            if (indent == 0) {
                if (trimmed.startsWith("squad:")) {
                    section = "squad";
                }
                continue; // nothing else at indent-0 is meaningful
            }

            // ── indent 2 — squad-level keys ───────────────────────────
            if (indent == 2) {
                // Flush current agent if we are leaving the agents section
                if (currentAgent != null) {
                    agents.add(currentAgent);
                    currentAgent = null;
                }

                if (trimmed.startsWith("llm:")) {
                    section = "llm";
                    continue;
                }
                if (trimmed.startsWith("agents:")) {
                    section = "agents";
                    continue;
                }

                // name / profile
                if (section.equals("squad") || section.equals("llm") || section.equals("agents")) {
                    KeyValue kv = parseKeyValue(trimmed);
                    if (kv != null) {
                        switch (kv.key()) {
                            case "name"    -> config.setName(kv.value());
                            case "profile" -> config.setProfile(kv.value());
                        }
                    }
                }
                continue;
            }

            // ── indent 4 — section content ────────────────────────────
            if (indent == 4) {
                if (section.equals("llm")) {
                    KeyValue kv = parseKeyValue(trimmed);
                    if (kv != null) {
                        switch (kv.key()) {
                            case "provider" -> llmConfig.setProvider(kv.value());
                            case "model"    -> llmConfig.setModel(kv.value());
                        }
                    }
                    continue;
                }

                if (section.equals("agents")) {
                    if (trimmed.startsWith("- ")) {
                        // New agent entry
                        if (currentAgent != null) agents.add(currentAgent);
                        currentAgent = new SquadConfig.AgentConfig();
                        String rest = trimmed.substring(2).stripLeading();
                        applyAgentField(currentAgent, rest);
                    } else if (currentAgent != null) {
                        // Continuation field on same agent (no dash)
                        applyAgentField(currentAgent, trimmed);
                    }
                    continue;
                }
            }

            // ── indent 6+ — agent field continuation ──────────────────
            if (indent >= 6 && section.equals("agents") && currentAgent != null) {
                applyAgentField(currentAgent, trimmed);
            }
        }

        // Flush last agent
        if (currentAgent != null) agents.add(currentAgent);

        // ── Validation ────────────────────────────────────────────────
        for (SquadConfig.AgentConfig a : agents) {
            if (a.getClassName() == null || a.getClassName().isBlank()) {
                throw new AgentConfigException(
                        "An agent entry in squad.yml is missing 'class'. "
                                + "Every agent must have: class: com.example.YourAgent"
                );
            }
            if (a.getTemperature() != null
                    && (a.getTemperature() < 0.0f || a.getTemperature() > 2.0f)) {
                throw new AgentConfigException(
                        "Invalid temperature '" + a.getTemperature()
                                + "' for agent '" + a.getClassName()
                                + "'. Must be between 0.0 and 2.0."
                );
            }
        }

        config.setLlm(llmConfig);
        config.setAgents(agents);
        return config;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static void applyAgentField(SquadConfig.AgentConfig agent, String line) {
        KeyValue kv = parseKeyValue(line);
        if (kv == null) return;
        switch (kv.key()) {
            case "class"       -> agent.setClassName(kv.value());
            case "role"        -> agent.setRole(kv.value().toUpperCase());
            case "temperature" -> agent.setTemperature(Float.parseFloat(kv.value()));
            case "max-tokens"  -> agent.setMaxTokens(Integer.parseInt(kv.value()));
            case "retry"       -> agent.setRetry(Integer.parseInt(kv.value()));
        }
    }

    private static KeyValue parseKeyValue(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) return null;
        String key   = line.substring(0, colon).strip();
        String value = line.substring(colon + 1).strip();
        // Remove surrounding quotes if present
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'")  && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value.isEmpty() ? null : new KeyValue(key, value);
    }

    private static int countLeadingSpaces(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else break;
        }
        return count;
    }

    private record KeyValue(String key, String value) {}
}