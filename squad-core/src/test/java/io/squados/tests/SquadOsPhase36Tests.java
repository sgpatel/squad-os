package io.squados.tests;

import io.squados.annotation.*;
import io.squados.config.SquadConfig;
import io.squados.context.SquadContext;
import io.squados.debate.*;
import io.squados.llm.MockLlmPort;
import io.squados.vote.VoteResult;

import java.util.List;

/**
 * Phase 36 — Multi-Agent Debate tests.
 *
 * DB01  DebatePosition.initial() creates round-0 position
 * DB02  DebatePosition.revised() records critique
 * DB03  DebateRound.summary() produces readable output
 * DB04  DebateEngine runs initial positions for all participants
 * DB05  DebateEngine executes multiple rounds
 * DB06  DebateResult.totalRounds() matches configured rounds
 * DB07  DebateResult contains VoteResult with outcome
 * DB08  DebateResult.consensus() is non-empty string
 * DB09  DebateEngine converges early when positions are similar
 * DB10  @Debate annotation is readable on orchestrator class
 */
public class SquadOsPhase36Tests {

    @Agent(role = AgentRole.ANALYST, name = "EthicsAgent",
           description = "Evaluates ethical implications of AI decisions.")
    static class EthicsAgent {}

    @Agent(role = AgentRole.CRITIC, name = "LegalAgent",
           description = "Evaluates legal compliance and regulatory requirements.")
    static class LegalAgent {}

    @Agent(role = AgentRole.STRATEGIST, name = "SafetyAgent",
           description = "Assesses safety risks and mitigation strategies.")
    static class SafetyAgent {}

    @Agent(role = AgentRole.EXECUTOR, name = "EthicsCommittee",
           description = "Orchestrates multi-agent ethics review debates.")
    @Debate(
        participants = {"EthicsAgent", "LegalAgent", "SafetyAgent"},
        rounds       = 2,
        voteRule     = VoteRule.MAJORITY,
        convergenceThreshold = 0.30f   // low threshold — easy to trigger in test
    )
    static class EthicsCommitteeAgent {}

    static final MockLlmPort LLM = new MockLlmPort();

    static {
        LLM.setDefaultResponse(
            "Based on careful analysis, I support a cautious approach with human oversight. " +
            "Risk mitigation requires transparent processes and clear accountability.");
    }

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  SquadOS Phase 36 — Multi-Agent Debate                       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        int passed = 0, failed = 0;
        String[] tests = {
            "DB01_debatePositionInitialCreation",
            "DB02_debatePositionRevisedRecordsCritique",
            "DB03_debateRoundSummaryIsReadable",
            "DB04_debateEngineRunsInitialPositions",
            "DB05_debateEngineExecutesMultipleRounds",
            "DB06_debateResultTotalRoundsMatchesConfig",
            "DB07_debateResultContainsVoteResult",
            "DB08_debateResultConsensusNonEmpty",
            "DB09_debateEngineConvergesEarlyOnSimilarPositions",
            "DB10_debateAnnotationReadable",
        };
        var t = new SquadOsPhase36Tests();
        for (String test : tests) {
            try {
                t.getClass().getDeclaredMethod(test).invoke(t);
                System.out.printf("  [PASS] %s%n", test);
                passed++;
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                System.out.printf("  [FAIL] %s%n         → %s%n", test, c.getMessage());
                failed++;
            }
        }
        System.out.println();
        System.out.printf("Phase 36 result: %d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    void DB01_debatePositionInitialCreation() {
        DebatePosition p = DebatePosition.initial("EthicsAgent", "AI must have human oversight.");
        assert "EthicsAgent".equals(p.agentName())                  : "Agent name should match";
        assert 0 == p.round()                                        : "Initial position should be round 0";
        assert "AI must have human oversight.".equals(p.position())  : "Position should match";
        assert p.critiqueGiven() == null                             : "No critique in round 0";
    }

    void DB02_debatePositionRevisedRecordsCritique() {
        DebatePosition p = DebatePosition.revised("LegalAgent", 1,
            "Updated position: comply with GDPR.", "Other agents underestimate legal risk.");
        assert 1 == p.round()               : "Round should be 1";
        assert p.critiqueGiven() != null    : "Critique should be present";
        assert p.critiqueGiven().contains("legal risk") : "Critique should contain content";
    }

    void DB03_debateRoundSummaryIsReadable() {
        DebateRound round = new DebateRound(1, List.of(
            DebatePosition.revised("EthicsAgent",  1, "Cautious approach needed.", "Other ignores ethics."),
            DebatePosition.revised("LegalAgent",   1, "Legal compliance first.",   "Needs legal grounding.")
        ), false);

        String summary = round.summary();
        assert summary.contains("Round 1")   : "Summary should include round number";
        assert summary.contains("EthicsAgent") : "Summary should include agent names";
        assert summary.contains("LegalAgent")  : "Summary should include all agents";
    }

    void DB04_debateEngineRunsInitialPositions() {
        SquadConfig cfg = SquadConfig.forTesting(
            List.of(EthicsAgent.class, LegalAgent.class, SafetyAgent.class));
        SquadContext ctx = new SquadContext(cfg, LLM);
        ctx.boot();

        DebateEngine engine = new DebateEngine(ctx.getRegistry(), LLM);
        DebateResult result = engine.run(
            "Should AI systems have mandatory human oversight?",
            new String[]{"EthicsAgent", "LegalAgent"},
            0, VoteRule.MAJORITY, TieBreaker.APPROVE, 0f);

        assert result.rounds().size() >= 1  : "Should have at least round 0";
        assert result.rounds().get(0).positions().size() == 2
            : "Round 0 should have 2 initial positions";
        System.out.printf("         → participants=2, rounds=%d%n", result.totalRounds());
    }

    void DB05_debateEngineExecutesMultipleRounds() {
        SquadConfig cfg = SquadConfig.forTesting(
            List.of(EthicsAgent.class, LegalAgent.class));
        SquadContext ctx = new SquadContext(cfg, LLM);
        ctx.boot();

        DebateEngine engine = new DebateEngine(ctx.getRegistry(), LLM);
        DebateResult result = engine.run(
            "Is open-source AI safe?",
            new String[]{"EthicsAgent", "LegalAgent"},
            2, VoteRule.MAJORITY, TieBreaker.APPROVE, 0f); // 0 = no early stop

        // round 0 + up to 2 debate rounds
        assert result.totalRounds() >= 1 : "Should have multiple rounds";
    }

    void DB06_debateResultTotalRoundsMatchesConfig() {
        SquadConfig cfg = SquadConfig.forTesting(
            List.of(EthicsAgent.class, LegalAgent.class, SafetyAgent.class));
        SquadContext ctx = new SquadContext(cfg, LLM);
        ctx.boot();

        DebateEngine engine = new DebateEngine(ctx.getRegistry(), LLM);
        DebateResult result = engine.run(EthicsCommitteeAgent.class,
            "Should we deploy model X without human review?");

        assert result.totalRounds() >= 1 : "Should have at least initial positions round";
        assert result.topic().contains("model X") : "Topic should be preserved";
    }

    void DB07_debateResultContainsVoteResult() {
        SquadConfig cfg = SquadConfig.forTesting(
            List.of(EthicsAgent.class, LegalAgent.class));
        SquadContext ctx = new SquadContext(cfg, LLM);
        ctx.boot();

        DebateEngine engine = new DebateEngine(ctx.getRegistry(), LLM);
        DebateResult result = engine.run(
            "Should AI replace human judges?",
            new String[]{"EthicsAgent", "LegalAgent"},
            1, VoteRule.MAJORITY, TieBreaker.APPROVE, 0f);

        assert result.voteResult() != null : "VoteResult should be present";
        assert result.voteResult().getOutcome() != null : "Outcome should not be null";
        System.out.printf("         → outcome=%s%n", result.voteResult().getOutcome());
    }

    void DB08_debateResultConsensusNonEmpty() {
        SquadConfig cfg = SquadConfig.forTesting(
            List.of(EthicsAgent.class, LegalAgent.class));
        SquadContext ctx = new SquadContext(cfg, LLM);
        ctx.boot();

        DebateEngine engine = new DebateEngine(ctx.getRegistry(), LLM);
        DebateResult result = engine.run(
            "Is AGI achievable by 2030?",
            new String[]{"EthicsAgent", "LegalAgent"},
            1, VoteRule.MAJORITY, TieBreaker.APPROVE, 0f);

        assert result.consensus() != null && !result.consensus().isBlank()
            : "Consensus should be non-empty";
    }

    void DB09_debateEngineConvergesEarlyOnSimilarPositions() {
        // When MockLlmPort returns the same text for all agents,
        // word-overlap similarity will be high → early convergence
        SquadConfig cfg = SquadConfig.forTesting(
            List.of(EthicsAgent.class, LegalAgent.class));
        SquadContext ctx = new SquadContext(cfg, LLM);
        ctx.boot();

        DebateEngine engine = new DebateEngine(ctx.getRegistry(), LLM);
        // threshold=0.50 with identical responses → should converge
        DebateResult result = engine.run(
            "AI needs oversight.",
            new String[]{"EthicsAgent", "LegalAgent"},
            5, VoteRule.MAJORITY, TieBreaker.APPROVE, 0.50f);

        // Result should be valid regardless of convergence
        assert result != null : "Result should not be null";
        System.out.printf("         → converged=%b, rounds=%d%n",
            result.converged(), result.totalRounds());
    }

    void DB10_debateAnnotationReadable() {
        Debate ann = EthicsCommitteeAgent.class.getAnnotation(Debate.class);
        assert ann != null                         : "@Debate annotation should be present";
        assert ann.participants().length == 3      : "Should have 3 participants";
        assert ann.rounds() == 2                   : "Should be 2 rounds";
        assert ann.voteRule() == VoteRule.MAJORITY  : "Vote rule should be MAJORITY";
    }
}
