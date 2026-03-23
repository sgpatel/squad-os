# Fraud Detection Squad

> Real-time payment fraud detection using **ALL 15 SquadOS annotations**.

## The Squad

| Agent | Role | Key Annotations |
|-------|------|-----------------|
| GatewayAgent | STRATEGIST | @OnEvent, @Delegate |
| RiskAnalystAgent | ANALYST | @SquadTool, @AutoPlan, @Eval, @Improve, @Traced |
| BehaviourAgent | RESEARCHER | @MissionProfile, @SquadTool, @OnMessage |
| ComplianceAgent | CRITIC | @SecureAgent, @Eval, @Traced |
| UnderwriterAgent | SUPPORT | @AutoApproval, @AwaitApproval, @OnMessage |

## All 15 Annotations

| Annotation | Where |
|-----------|-------|
| `@Agent` | All 5 agents |
| `@OnEvent` | GatewayAgent — triggered by payment events |
| `@Delegate` | GatewayAgent — FIRST_MATCH routing to specialist |
| `@SquadTool` | RiskAnalyst — 4 tools: history, velocity, IP blocklist, merchant risk |
| `@SquadPlan` | 4 typed outputs: PaymentEvent, RiskAssessment, FraudReport, PaymentDecision |
| `@AutoPlan` | RiskAnalyst — iterates until confidence >= threshold |
| `@SquadVote` | 3 agents vote FRAUD/LEGIT (MAJORITY rule, TieBreaker.ESCALATE) |
| `@AutoApproval` | riskScore < 0.3 AND velocity < 5 — instant pass |
| `@AwaitApproval` | riskScore > 0.7 — escalate to senior-fraud-analyst (4h timeout) |
| `@Eval` | ComplianceAgent quality gate (minScore=0.8, retryOnFail=true) |
| `@Improve` | RiskAnalyst — learns from past correct/wrong decisions (topK=3) |
| `@Traced` | All agents — spans + token counts via TokenTrackingLlmPort |
| `@SecureAgent` | ComplianceAgent — requires compliance JWT role |
| `@MissionProfile` | BehaviourAgent — activated on "work" profile |
| `@OnMessage` | UnderwriterAgent — listens for risk + behaviour reports |

## Architecture

```
Payment event (payments.incoming)
    │
    ▼ @OnEvent
GatewayAgent
    │
    ▼ @Delegate (FIRST_MATCH)
    ├── High value/suspicious → RiskAnalystAgent
    └── Pattern/geo          → BehaviourAgent
         │
         ▼ @SquadTool (4 tools)
         ▼ @AutoPlan (iterate until COMPLETE)
         ▼ @Eval (quality gate)
         ▼ @Improve (few-shot enrichment)
         │
         ▼ @SquadVote (3 agents, MAJORITY)
         │
         ├── riskScore < 0.3 → @AutoApproval → APPROVED
         └── riskScore > 0.7 → @AwaitApproval → ESCALATED
                                    │
                                    ▼ @SquadPlan
                                PaymentDecision
```

## Run

```bash
# Prerequisites
ollama pull llama3.2
docker run -d -p 6379:6379 redis:7-alpine  # for shared tracing

# Run
cd squad-examples/fraud-detection
mvn spring-boot:run
```

## Expected Output

**Scenario 1 — Low Risk (C-1042, GBP 4,200, GB, known device):**
```
[RiskAnalyst] Risk Level:  LOW
[RiskAnalyst] Risk Score:  0.18
[SquadVote]   Result: APPROVED (3-0)
[AutoApproval] riskScore 0.18 < 0.3 — AUTO-APPROVED
[Telemetry @Traced]
  Spans:   3
  Tokens:  1,247
  Latency: 6,499ms
```

**Scenario 2 — High Risk (C-9999, GBP 48,000, NG, Tor IP, unknown merchant):**
```
[RiskAnalyst] Risk Level:  CRITICAL
[RiskAnalyst] Risk Score:  1.0
[SquadVote]   Result: REJECTED (0-3)
[AwaitApproval] riskScore 1.0 > 0.7 — ESCALATING to senior-fraud-analyst
[Telemetry @Traced]
  Spans:   3
  Tokens:  1,891
  Latency: 5,973ms
```

## pom.xml

```xml
<dependency>
  <groupId>io.github.sgpatel</groupId>
  <artifactId>squad-spring-boot-starter</artifactId>
  <version>3.4.0</version>
</dependency>
<dependency>
  <groupId>redis.clients</groupId>
  <artifactId>jedis</artifactId>
  <version>5.1.0</version>
</dependency>
```
