package io.squados.delegate;

import io.squados.annotation.AgentRole;
import io.squados.annotation.DelegateStrategy;
import java.time.Instant;

/**
 * The result of a @Delegate routing decision.
 * Immutable — created by DelegateRouter after selecting a target.
 */
public class DelegationDecision {

    private final AgentRole        chosenRole;
    private final AgentRole[]      candidates;
    private final DelegateStrategy strategy;
    private final String           reasoning;   // why this role was chosen
    private final Instant          decidedAt;
    private final boolean          usedFallback;

    public DelegationDecision(AgentRole chosenRole, AgentRole[] candidates,
                               DelegateStrategy strategy, String reasoning,
                               boolean usedFallback) {
        this.chosenRole   = chosenRole;
        this.candidates   = candidates;
        this.strategy     = strategy;
        this.reasoning    = reasoning;
        this.decidedAt    = Instant.now();
        this.usedFallback = usedFallback;
    }

    public AgentRole        getChosenRole()  { return chosenRole; }
    public AgentRole[]      getCandidates()  { return candidates; }
    public DelegateStrategy getStrategy()    { return strategy; }
    public String           getReasoning()   { return reasoning; }
    public Instant          getDecidedAt()   { return decidedAt; }
    public boolean          isUsedFallback() { return usedFallback; }

    @Override
    public String toString() {
        return String.format("DelegationDecision{role=%s, strategy=%s, fallback=%b, reason=%s}",
            chosenRole, strategy, usedFallback, reasoning);
    }
}