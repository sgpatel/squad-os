package io.squados.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for SquadOS Spring Boot Starter.
 *
 * All properties are prefixed with {@code squad.}
 *
 * Reference:
 * <pre>
 * squad.name=my-squad
 * squad.llm.provider=ollama
 * squad.llm.model=llama3.2
 * squad.llm.temperature=0.5
 * squad.llm.max-tokens=2048
 * squad.tracing.enabled=true
 * squad.tracing.exporter=memory          # memory | redis
 * redis.host=localhost
 * redis.port=6379
 * squad.security.enabled=false
 * squad.security.jwt-issuer=squados
 * squad.security.audit-log=true
 * squad.approval.enabled=true
 * squad.memory.enabled=false
 * squad.memory.store=memory              # memory | redis | pgvector
 * squad.memory.top-k=3
 * squad.memory.min-score=0.72
 * squad.conversation.enabled=false
 * squad.conversation.max-turns=20
 * squad.rate-limit.default-token-budget=0
 * squad.mcp.enabled=false
 * squad.mcp.timeout-ms=5000
 * squad.durable.enabled=false
 * squad.durable.store=memory             # memory | redis
 * squad.durable.ttl-hours=24
 * squad.guardrails.enabled=false
 * squad.agent-api.enabled=false
 * squad.api.key=                         # required for ApiKeyAuth
 * </pre>
 */
@ConfigurationProperties(prefix = "squad")
public class SquadProperties {

    private String name = "my-squad";
    private Llm         llm          = new Llm();
    private Tracing     tracing      = new Tracing();
    private Security    security     = new Security();
    private Approval    approval     = new Approval();
    private Memory      memory       = new Memory();
    private Conversation conversation = new Conversation();
    private RateLimit   rateLimit    = new RateLimit();
    private Mcp         mcp          = new Mcp();
    private Durable     durable      = new Durable();
    private Guardrails  guardrails   = new Guardrails();
    private AgentApi    agentApi     = new AgentApi();
    private Api         api          = new Api();
    private Redis       redis        = new Redis();

    public String       getName()        { return name; }
    public void         setName(String v){ this.name = v; }
    public Llm          getLlm()         { return llm; }
    public Tracing      getTracing()     { return tracing; }
    public Security     getSecurity()    { return security; }
    public Approval     getApproval()    { return approval; }
    public Memory       getMemory()      { return memory; }
    public Conversation getConversation(){ return conversation; }
    public RateLimit    getRateLimit()   { return rateLimit; }
    public Mcp          getMcp()         { return mcp; }
    public Durable      getDurable()     { return durable; }
    public Guardrails   getGuardrails()  { return guardrails; }
    public AgentApi     getAgentApi()    { return agentApi; }
    public Api          getApi()         { return api; }
    public Redis        getRedis()       { return redis; }

    public static class Llm {
        private String  provider    = "ollama";
        private String  model       = "llama3.2";
        private float   temperature = 0.5f;
        private int     maxTokens   = 2048;

        public String getProvider()           { return provider; }
        public void   setProvider(String v)   { provider = v; }
        public String getModel()              { return model; }
        public void   setModel(String v)      { model = v; }
        public float  getTemperature()        { return temperature; }
        public void   setTemperature(float v) { temperature = v; }
        public int    getMaxTokens()          { return maxTokens; }
        public void   setMaxTokens(int v)     { maxTokens = v; }
    }

    public static class Tracing {
        private boolean enabled  = true;
        private String  exporter = "memory";  // memory | redis | log
        private String  redisKey = "squados:traces";

        public boolean isEnabled()           { return enabled; }
        public void    setEnabled(boolean v) { enabled = v; }
        public String  getExporter()         { return exporter; }
        public void    setExporter(String v) { exporter = v; }
        public String  getRedisKey()         { return redisKey; }
        public void    setRedisKey(String v) { redisKey = v; }
    }

    public static class Security {
        private boolean enabled    = false;
        private String  jwtIssuer  = "squados";
        private boolean auditLog   = true;

        public boolean isEnabled()              { return enabled; }
        public void    setEnabled(boolean v)    { enabled = v; }
        public String  getJwtIssuer()           { return jwtIssuer; }
        public void    setJwtIssuer(String v)   { jwtIssuer = v; }
        public boolean isAuditLog()             { return auditLog; }
        public void    setAuditLog(boolean v)   { auditLog = v; }
    }

    public static class Approval {
        private boolean enabled = true;
        public boolean isEnabled()           { return enabled; }
        public void    setEnabled(boolean v) { enabled = v; }
    }

    public static class Memory {
        private boolean enabled  = false;
        private String  store    = "memory";   // memory | redis | pgvector
        private int     topK     = 3;
        private float   minScore = 0.72f;

        public boolean isEnabled()           { return enabled; }
        public void    setEnabled(boolean v) { enabled = v; }
        public String  getStore()            { return store; }
        public void    setStore(String v)    { store = v; }
        public int     getTopK()             { return topK; }
        public void    setTopK(int v)        { topK = v; }
        public float   getMinScore()         { return minScore; }
        public void    setMinScore(float v)  { minScore = v; }
    }

    public static class Conversation {
        private boolean enabled  = false;
        private int     maxTurns = 20;

        public boolean isEnabled()           { return enabled; }
        public void    setEnabled(boolean v) { enabled = v; }
        public int     getMaxTurns()         { return maxTurns; }
        public void    setMaxTurns(int v)    { maxTurns = v; }
    }

    public static class RateLimit {
        private int defaultTokenBudget = 0;  // 0 = unlimited

        public int  getDefaultTokenBudget()    { return defaultTokenBudget; }
        public void setDefaultTokenBudget(int v){ defaultTokenBudget = v; }
    }

    public static class Mcp {
        private boolean enabled   = false;
        private int     timeoutMs = 5000;

        public boolean isEnabled()           { return enabled; }
        public void    setEnabled(boolean v) { enabled = v; }
        public int     getTimeoutMs()        { return timeoutMs; }
        public void    setTimeoutMs(int v)   { timeoutMs = v; }
    }

    public static class Durable {
        private boolean enabled  = false;
        private String  store    = "memory";  // memory | redis
        private int     ttlHours = 24;

        public boolean isEnabled()           { return enabled; }
        public void    setEnabled(boolean v) { enabled = v; }
        public String  getStore()            { return store; }
        public void    setStore(String v)    { store = v; }
        public int     getTtlHours()         { return ttlHours; }
        public void    setTtlHours(int v)    { ttlHours = v; }
    }

    public static class Guardrails {
        private boolean enabled = false;

        public boolean isEnabled()           { return enabled; }
        public void    setEnabled(boolean v) { enabled = v; }
    }

    public static class AgentApi {
        private boolean enabled = false;

        public boolean isEnabled()           { return enabled; }
        public void    setEnabled(boolean v) { enabled = v; }
    }

    public static class Api {
        private String key = "";

        public String getKey()           { return key; }
        public void   setKey(String v)   { key = v; }
    }

    public static class Redis {
        private String host     = "localhost";
        private int    port     = 6379;
        private String password = "";

        public String getHost()              { return host; }
        public void   setHost(String v)      { host = v; }
        public int    getPort()              { return port; }
        public void   setPort(int v)         { port = v; }
        public String getPassword()          { return password; }
        public void   setPassword(String v)  { password = v; }
    }
}
