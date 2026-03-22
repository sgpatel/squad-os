# Fraud Detection Squad

> Real-time payment fraud detection using ALL 15 SquadOS annotations.

## The Squad

| Agent | Role | Responsibility |
|-------|------|----------------|
| **GatewayAgent** | STRATEGIST | Receives payments, delegates to specialists |
| **RiskAnalyst** | ANALYST | Scores transactions, queries history + velocity |
| **BehaviourAgent** | RESEARCHER | Analyses behaviour patterns, geo-risk |
| **ComplianceAgent** | CRITIC | Enforces regulations, requires JWT auth |
| **UnderwriterAgent** | SUPPORT | Makes final approve/reject decision |

## All 15 annotations in use

| Annotation | Where |
|-----------|-------|
| `@Agent` | All 5 agents |
| `@OnEvent` | GatewayAgent — triggered by Kafka payment events |
| `@Delegate` | GatewayAgent — routes to ANALYST or RESEARCHER based on amount |
| `@SquadTool` | RiskAnalyst — getTransactionHistory, checkVelocity, checkIPBlocklist |
| `@SquadPlan` | Typed: PaymentEvent, RiskAssessment, FraudReport, PaymentDecision |
| `@AutoPlan` | RiskAnalyst — iterates until confidence >= threshold |
| `@SquadVote` | 3 agents vote FRAUD / LEGIT (MAJORITY rule) |
| `@AutoApproval` | riskScore < 0.3 AND velocity < 5 — instant pass |
| `@AwaitApproval` | riskScore > 0.8 OR amount > 50000 — human review |
| `@Eval` | ComplianceAgent — quality gate on risk reports |
| `@Improve` | RiskAnalyst — learns from past correct/wrong decisions |
| `@Traced` | All agents — token usage, latency, spans to Jaeger |
| `@SecureAgent` | ComplianceAgent — requires compliance JWT role |
| `@Memory` | BehaviourAgent — remembers customer patterns |
| `@OnMessage` | UnderwriterAgent — receives vote results from bus |

## Run

```bash
ollama pull llama3.2
cd squad-examples/fraud-detection
mvn spring-boot:run
```

## Example output

```
[GatewayAgent]    Payment received: £4,200 from customer C-1042
[Delegate]        LLM_CHOICE: routed to ANALYST
[RiskAnalyst]     Checking transaction history...
[RiskAnalyst]     Velocity: 3 transactions in last hour (OK)
[RiskAnalyst]     IP: Not on blocklist
[RiskAnalyst]     Risk score: 0.18 — LOW RISK
[BehaviourAgent]  Customer C-1042: consistent purchase pattern, known merchant
[ComplianceAgent] GRANTED: compliance-officer called by system [compliance]
[SquadVote]       Vote received from RiskAnalyst:  LEGIT — risk 0.18
[SquadVote]       Vote received from BehaviourAgent: LEGIT — normal pattern
[SquadVote]       Vote received from ComplianceAgent: LEGIT — no red flags
[AutoApproval]    riskScore 0.18 < 0.3 — AUTO-APPROVED
DECISION: APPROVED | Transaction processed | 847ms | 1,203 tokens
```
