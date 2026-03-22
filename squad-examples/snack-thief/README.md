# 🍕 The Great Office Snack Thief Investigation Squad

> "Someone stole Dave's leftover pizza. This. Will. Not. Stand."

The most over-engineered solution to office fridge crime ever built.
A multi-agent AI detective squad that uses EVERY SquadOS feature to
investigate, vote on, and deliver justice for stolen office snacks.

## The Squad

| Agent | Role | Personality |
|-------|------|-------------|
| **SherlockBot** | STRATEGIST | Dramatic, convinced it's always the intern |
| **CSI-GPT** | ANALYST | Obsessed with evidence, quotes crime shows |
| **GossipAgent** | RESEARCHER | Knows EVERYTHING about everyone in the office |
| **HRBot** | SUPPORT | Terrified of lawsuits, overly cautious |
| **JudgeJudy** | CRITIC | Zero tolerance. "You are DISMISSED." |

## Features Used

| Feature | How it's used |
|---------|---------------|
| `@OnEvent` | Triggered when someone reports a stolen snack |
| `@SquadTool` | Query fridge access log, CCTV stub, HR records |
| `@SquadPlan` | Typed `CrimeReport`, `SuspectProfile`, `Verdict` |
| `@SquadVote` | All 5 agents vote on the culprit |
| `@AutoApproval` | Auto-close if suspect left crumbs at the scene |
| `@AwaitApproval` | Escalate to real HR if suspect is the CEO |
| `@Eval` | Judge quality of the investigation |
| `@AutoPlan` | Iterative investigation — keeps digging until SOLVED |
| `@Memory` | Remembers past snack crimes (serial offenders beware) |
| `@Traced` | Full observability — track every agent's work |
| `@OnMessage` | Agents gossip with each other mid-investigation |
| `@AgentCircuitBreaker` | GossipAgent trips circuit if they go too far |

## Run

```bash
ollama pull llama3.2
cd squad-examples/snack-thief
mvn spring-boot:run
```

## Example Output

```
🚨 SNACK ALERT: Dave's leftover pizza has been STOLEN

[SherlockBot]  Elementary. It's always the intern.
[CSI-GPT]      Fridge log shows badge #4471 at 12:03pm. DNA pending.
[GossipAgent]  I HEARD that Karen from accounting has been "dieting" suspiciously...
[HRBot]        I'm going to need everyone to fill out form HR-2024-SNACK before we proceed.

⚖️  VOTE: Who stole the pizza?
  [APPROVE Karen] SherlockBot  — "The evidence points to Karen. Classic."
  [APPROVE Karen] CSI-GPT      — "Badge scan confirmed. Case closed."
  [APPROVE Karen] GossipAgent  — "I ALWAYS knew it was Karen."
  [REJECT  Karen] HRBot        — "We can't accuse without a signed confession."
  [APPROVE Karen] JudgeJudy    — "Karen. You are DISMISSED."

VERDICT: GUILTY (4-1 majority)
Sentence: Karen must replace the pizza AND bring donuts on Friday.
```
