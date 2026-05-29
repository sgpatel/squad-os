package io.squados.debate;

import io.squados.annotation.*;
import io.squados.context.AgentWrapper;
import io.squados.context.AgentRegistry;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;
import io.squados.vote.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates multi-agent debates declared via {@literal @}Debate.
 *
 * Protocol per round:
 *   1. Each participant receives the topic + all previous positions
 *   2. Each participant produces:
 *      a. A critique of all other agents' positions
 *      b. A revised position incorporating critiques received
 *   3. Convergence is checked (all positions similar enough → stop early)
 *   4. After all rounds, VoteCollector tallies to produce a final outcome
 *
 * Usage:
 * <pre>
 *   DebateEngine engine = new DebateEngine(registry, llm);
 *   DebateResult result = engine.run(
 *       EthicsCommitteeAgent.class,
 *       "Should we deploy this model without human review?"
 *   );
 *   System.out.println("Consensus: " + result.consensus());
 * </pre>
 */
public class DebateEngine {

    private final AgentRegistry registry;
    private final LlmPort       llm;

    public DebateEngine(AgentRegistry registry, LlmPort llm) {
        this.registry = registry;
        this.llm      = llm;
    }

    /**
     * Run a debate as declared by {@literal @}Debate on {@code orchestratorClass}.
     */
    public DebateResult run(Class<?> orchestratorClass, String topic) {
        Debate ann = orchestratorClass.getAnnotation(Debate.class);
        if (ann == null) throw new IllegalArgumentException(
            orchestratorClass.getSimpleName() + " is not annotated with @Debate");
        return run(topic, ann.participants(), ann.rounds(), ann.voteRule(),
            ann.tieBreaker(), ann.convergenceThreshold());
    }

    /**
     * Run a debate with explicit parameters.
     */
    public DebateResult run(String topic, String[] participantNames,
                            int maxRounds, VoteRule voteRule, TieBreaker tieBreaker,
                            float convergenceThreshold) {
        long start = System.currentTimeMillis();
        System.out.printf("[Debate] Starting — topic='%s', participants=%s, rounds=%d%n",
            topic.substring(0, Math.min(80, topic.length())),
            Arrays.toString(participantNames), maxRounds);

        List<DebateRound> rounds = new ArrayList<>();

        // Round 0: initial positions (independent, no context of others)
        List<DebatePosition> currentPositions = initialPositions(participantNames, topic);
        rounds.add(new DebateRound(0, List.copyOf(currentPositions), false));

        boolean converged = false;

        for (int round = 1; round <= maxRounds && !converged; round++) {
            List<DebatePosition> nextPositions = new ArrayList<>();
            String positionContext = buildPositionContext(currentPositions);

            for (String agentName : participantNames) {
                DebatePosition current = findPosition(currentPositions, agentName);
                if (current == null) continue;

                // Step a: critique all other positions
                String critique = generateCritique(agentName, current.position(),
                    positionContext, topic);

                // Step b: revise own position given critiques
                String revised = generateRevision(agentName, current.position(),
                    positionContext, critique, topic);

                nextPositions.add(DebatePosition.revised(agentName, round, revised, critique));
            }

            // Check convergence
            converged = convergenceThreshold > 0
                && checkConvergence(nextPositions, convergenceThreshold);

            rounds.add(new DebateRound(round, List.copyOf(nextPositions), converged));
            currentPositions = nextPositions;

            System.out.printf("[Debate] Round %d complete — converged=%b%n", round, converged);
        }

        // Final vote: each agent's last position → Vote
        VoteCollector collector = new VoteCollector(
            topic, voteRule, tieBreaker, participantNames.length, 60);
        String consensus = buildConsensus(currentPositions);
        for (DebatePosition p : currentPositions) {
            collector.submit(Vote.approve(p.position().substring(
                0, Math.min(200, p.position().length()))), p.agentName());
        }
        VoteResult voteResult;
        try {
            voteResult = collector.resolve();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            voteResult = new VoteResult(VoteResult.Outcome.TIMEOUT, List.of(), topic);
        }

        long elapsed = System.currentTimeMillis() - start;
        return new DebateResult(topic, rounds, voteResult, consensus, converged, elapsed);
    }

    // ── Protocol steps ────────────────────────────────────────────────

    private List<DebatePosition> initialPositions(String[] agents, String topic) {
        List<DebatePosition> positions = new ArrayList<>();
        for (String agentName : agents) {
            String systemPrompt = resolveSystemPrompt(agentName);
            String prompt = "Provide your initial position on the following topic:\n\n" + topic
                + "\n\nBe clear, reasoned, and concise (2-4 sentences).";
            String position = callLlm(systemPrompt, prompt);
            positions.add(DebatePosition.initial(agentName, position));
            System.out.printf("[Debate] Initial position from %s: %s...%n",
                agentName, position.substring(0, Math.min(60, position.length())));
        }
        return positions;
    }

    private String generateCritique(String agentName, String ownPosition,
                                    String otherPositions, String topic) {
        String systemPrompt = resolveSystemPrompt(agentName);
        String prompt = "Topic: " + topic + "\n\n"
            + "Your current position: " + ownPosition + "\n\n"
            + "Other participants' positions:\n" + otherPositions + "\n\n"
            + "Critically evaluate the other positions. "
            + "What are their weaknesses or blind spots? "
            + "What valid points do they raise that you should consider? "
            + "Be analytical and constructive (2-3 sentences).";
        return callLlm(systemPrompt, prompt);
    }

    private String generateRevision(String agentName, String ownPosition,
                                    String otherPositions, String critique, String topic) {
        String systemPrompt = resolveSystemPrompt(agentName);
        String prompt = "Topic: " + topic + "\n\n"
            + "Your previous position: " + ownPosition + "\n\n"
            + "Other participants' positions:\n" + otherPositions + "\n\n"
            + "Your critique of their positions: " + critique + "\n\n"
            + "Now revise your position, incorporating valid insights from other participants "
            + "while maintaining your core reasoning where it stands. "
            + "Aim for a well-rounded, defensible position (2-4 sentences).";
        return callLlm(systemPrompt, prompt);
    }

    // ── Convergence ───────────────────────────────────────────────────

    private boolean checkConvergence(List<DebatePosition> positions, float threshold) {
        if (positions.size() < 2) return true;
        // Simple word-overlap similarity between all pairs
        for (int i = 0; i < positions.size(); i++) {
            for (int j = i + 1; j < positions.size(); j++) {
                float sim = wordOverlapSimilarity(
                    positions.get(i).position(), positions.get(j).position());
                if (sim < threshold) return false;
            }
        }
        return true;
    }

    private float wordOverlapSimilarity(String a, String b) {
        Set<String> wordsA = tokenize(a);
        Set<String> wordsB = tokenize(b);
        Set<String> intersection = new HashSet<>(wordsA);
        intersection.retainAll(wordsB);
        int union = wordsA.size() + wordsB.size() - intersection.size();
        return union == 0 ? 1f : (float) intersection.size() / union;
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("[\\s\\p{Punct}]+"))
            .filter(w -> w.length() > 3) // skip short stop-words
            .collect(Collectors.toSet());
    }

    // ── Consensus building ────────────────────────────────────────────

    private String buildConsensus(List<DebatePosition> positions) {
        if (positions.isEmpty()) return "No consensus reached.";
        // Use LLM to synthesise consensus from final positions
        String allPositions = positions.stream()
            .map(p -> "[" + p.agentName() + "]: " + p.position())
            .collect(Collectors.joining("\n\n"));
        String prompt = "The following agents have reached their final positions:\n\n"
            + allPositions + "\n\n"
            + "Synthesise a single consensus statement that captures the common ground "
            + "and key agreements. Be concise (1-3 sentences).";
        return callLlm("You are a neutral consensus builder.", prompt);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String buildPositionContext(List<DebatePosition> positions) {
        return positions.stream()
            .map(p -> "[" + p.agentName() + "]: " + p.position())
            .collect(Collectors.joining("\n\n"));
    }

    private DebatePosition findPosition(List<DebatePosition> positions, String agentName) {
        return positions.stream().filter(p -> p.agentName().equals(agentName))
            .findFirst().orElse(null);
    }

    private String resolveSystemPrompt(String agentName) {
        AgentWrapper wrapper = registry.getByName(agentName);
        if (wrapper != null) {
            return wrapper.buildSystemPrompt(
                new io.squados.agent.TaskContext("debate"));
        }
        return "You are " + agentName + ". Provide thoughtful, reasoned analysis.";
    }

    private String callLlm(String systemPrompt, String userMessage) {
        LlmOptions opts = new LlmOptions(0.5f, 1024, null);
        LlmResponse r = llm.chat(systemPrompt, userMessage, opts);
        return r != null && r.content() != null ? r.content().trim() : "(no response)";
    }
}
