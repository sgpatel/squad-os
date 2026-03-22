package io.squados.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for SquadOS Spring Boot Starter.
 *
 * All properties are prefixed with {@code squad.}
 *
 * Example application.properties:
 * <pre>
 * squad.name=my-squad
 * squad.llm.provider=ollama
 * squad.llm.model=llama3.2
 * squad.llm.temperature=0.5
 * squad.tracing.enabled=true
 * squad.security.enabled=false
 * squad.approval.enabled=true
 * </pre>
 */
@ConfigurationProperties(prefix = "squad")
public class SquadProperties {

    /** Squad name shown in boot banner. */
    private String name = "my-squad";

    /** LLM connection settings. */
    private Llm llm = new Llm();

    /** Tracing settings. */
    private Tracing tracing = new Tracing();

    /** Security settings. */
    private Security security = new Security();

    /** Approval pipeline settings. */
    private Approval approval = new Approval();

    public String  getName()    { return name; }
    public void    setName(String name) { this.name = name; }
    public Llm     getLlm()     { return llm; }
    public Tracing getTracing() { return tracing; }
    public Security getSecurity() { return security; }
    public Approval getApproval() { return approval; }

    public static class Llm {
        private String  provider    = "ollama";
        private String  model       = "llama3.2";
        private float   temperature = 0.5f;
        private int     maxTokens   = 2048;

        public String getProvider()    { return provider; }
        public void   setProvider(String v) { provider = v; }
        public String getModel()       { return model; }
        public void   setModel(String v)    { model = v; }
        public float  getTemperature() { return temperature; }
        public void   setTemperature(float v) { temperature = v; }
        public int    getMaxTokens()   { return maxTokens; }
        public void   setMaxTokens(int v)   { maxTokens = v; }
    }

    public static class Tracing {
        private boolean enabled   = true;
        private String  exporter  = "log";  // log | memory | jaeger
        private String  jaegerUrl = "http://localhost:14268/api/traces";

        public boolean isEnabled()     { return enabled; }
        public void    setEnabled(boolean v) { enabled = v; }
        public String  getExporter()   { return exporter; }
        public void    setExporter(String v) { exporter = v; }
        public String  getJaegerUrl()  { return jaegerUrl; }
        public void    setJaegerUrl(String v) { jaegerUrl = v; }
    }

    public static class Security {
        private boolean enabled       = false;
        private String  jwtIssuer     = "squados";
        private boolean auditLog      = true;

        public boolean isEnabled()       { return enabled; }
        public void    setEnabled(boolean v) { enabled = v; }
        public String  getJwtIssuer()    { return jwtIssuer; }
        public void    setJwtIssuer(String v) { jwtIssuer = v; }
        public boolean isAuditLog()      { return auditLog; }
        public void    setAuditLog(boolean v) { auditLog = v; }
    }

    public static class Approval {
        private boolean enabled = true;

        public boolean isEnabled()       { return enabled; }
        public void    setEnabled(boolean v) { enabled = v; }
    }
}