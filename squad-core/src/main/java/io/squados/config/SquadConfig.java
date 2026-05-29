package io.squados.config;

import io.squados.annotation.AgentRole;
import io.squados.llm.LlmOptions;
import java.util.ArrayList;
import java.util.List;

/**
 * Typed representation of the full squad.yml file.
 *
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
 */
public class SquadConfig {

    private String       name    = "unnamed-squad";
    private String       profile = "default";
    private LlmConfig    llm     = new LlmConfig();
    private List<AgentConfig> agents = new ArrayList<>();

    // ── Getters / setters ─────────────────────────────────────────────

    public String            getName()    { return name; }
    public String            getProfile() { return profile; }
    public LlmConfig         getLlm()     { return llm; }
    public List<AgentConfig> getAgents()  { return agents; }

    public void setName(String name)               { this.name = name; }
    public void setProfile(String profile)         { this.profile = profile; }
    public void setLlm(LlmConfig llm)             { this.llm = llm; }
    public void setAgents(List<AgentConfig> agents){ this.agents = agents; }

    /**
     * Retrieve the AgentConfig for a given class name.
     * Returns null if no matching config entry exists.
     */
    public AgentConfig forClass(Class<?> cls) {
        return agents.stream()
                .filter(a -> a.getClassName().equals(cls.getName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Create a minimal SquadConfig from a list of agent classes for unit tests.
     * Sets provider to "mock", model to "mock-model", and populates the agents
     * list from the given classes.
     */
    public static SquadConfig forTesting(java.util.List<Class<?>> agentClasses) {
        SquadConfig cfg = new SquadConfig();
        cfg.setName("test-squad");
        cfg.setProfile("test");
        LlmConfig llm = new LlmConfig();
        llm.setProvider("mock");
        llm.setModel("mock-model");
        cfg.setLlm(llm);
        java.util.List<AgentConfig> agentCfgs = new java.util.ArrayList<>();
        for (Class<?> cls : agentClasses) {
            AgentConfig ac = new AgentConfig();
            ac.setClassName(cls.getName());
            agentCfgs.add(ac);
        }
        cfg.setAgents(agentCfgs);
        return cfg;
    }

    /** Fluent builder — convenience for tests and programmatic construction. */
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final SquadConfig cfg = new SquadConfig();
        public Builder name(String name)       { cfg.setName(name); return this; }
        public Builder profile(String profile) { cfg.setProfile(profile); return this; }
        public SquadConfig build()             { return cfg; }
    }

    @Override
    public String toString() {
        return "SquadConfig{name='" + name + "', profile='" + profile
                + "', provider='" + llm.getProvider()
                + "', agents=" + agents.size() + "}";
    }

    // ══════════════════════════════════════════════════════════════════
    // Inner: LlmConfig
    // ══════════════════════════════════════════════════════════════════

    public static class LlmConfig {
        private String provider = "anthropic";
        private String model    = "claude-sonnet-4-6";

        public String getProvider() { return provider; }
        public String getModel()    { return model; }
        public void setProvider(String p) { this.provider = p; }
        public void setModel(String m)    { this.model = m; }

        @Override
        public String toString() {
            return "LlmConfig{provider='" + provider + "', model='" + model + "'}";
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Inner: AgentConfig — per-agent configuration from squad.yml
    // ══════════════════════════════════════════════════════════════════

    public static class AgentConfig {

        /** Fully qualified class name, e.g. "com.example.OracleAgent" */
        private String className;

        /**
         * Optional explicit role — normally inferred from @Agent annotation.
         * Useful for roles that differ from the annotation default.
         */
        private String role;

        /**
         * Temperature override. If null, AgentRole.defaultOptions() is used.
         */
        private Float  temperature;

        /**
         * maxTokens override. If null, AgentRole.defaultOptions() is used.
         */
        private Integer maxTokens;

        /** Number of retries before marking an agent as failed */
        private int retry = 1;

        // Getters
        public String  getClassName()   { return className; }
        public String  getRole()        { return role; }
        public Float   getTemperature() { return temperature; }
        public Integer getMaxTokens()   { return maxTokens; }
        public int     getRetry()       { return retry; }

        // Setters (used by SnakeYAML)
        public void setClassName(String c)   { this.className = c; }
        public void setRole(String r)        { this.role = r; }
        public void setTemperature(Float t)  { this.temperature = t; }
        public void setMaxTokens(Integer m)  { this.maxTokens = m; }
        public void setRetry(int r)          { this.retry = r; }

        /**
         * Resolve the effective LlmOptions for this agent.
         * Priority: explicit yml values &gt; AgentRole defaults &gt; LlmOptions.defaults()
         */
        public LlmOptions resolveOptions(AgentRole agentRole, String globalModel) {
            LlmOptions roleDefaults = (agentRole != null)
                    ? agentRole.defaultOptions()
                    : LlmOptions.defaults();

            float  temp   = (temperature != null) ? temperature   : roleDefaults.temperature();
            int    tokens = (maxTokens   != null) ? maxTokens     : roleDefaults.maxTokens();
            String model  = (roleDefaults.model() != null) ? roleDefaults.model() : globalModel;

            return new LlmOptions(temp, tokens, model);
        }

        @Override
        public String toString() {
            return "AgentConfig{class='" + className
                    + "', temperature=" + temperature
                    + ", maxTokens=" + maxTokens + "}";
        }
    }
}
