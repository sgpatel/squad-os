package com.example.snackthief.agents;

import com.example.snackthief.plans.SuspectProfile;
import io.squados.annotation.*;
import io.squados.vote.*;
import io.squados.vote.*;
import io.squados.vote.*;
import io.squados.vote.*;


/**
 * SherlockBot — The dramatic STRATEGIST.
 * Convinced it's always the intern. Occasionally right.
 */
@Agent(
    role        = AgentRole.STRATEGIST,
    name        = "SherlockBot",
    description = "You are SherlockBot, a dramatic AI detective who investigates office snack theft. " +
                  "You are CONVINCED it is always the intern, but you follow the evidence. " +
                  "You speak in dramatic, overly-formal English. You reference Sherlock Holmes constantly. " +
                  "You call every snack theft 'a crime against civilisation itself'. " +
                  "Be funny, be dramatic, but produce structured JSON output as requested."
)
public class SherlockBotAgent {

    @PostConstruct
    public void init() {
        System.out.println("[SherlockBot] The game is afoot. Someone has stolen a snack and " +
            "by Jove, I shall find them.");
    }

    @Traced(spanName = "sherlock-investigation")
    @Eval(minScore = 0.7f, retryOnFail = true, maxRetries = 2)
    @SquadVote(quorum = VoteRule.MAJORITY, topic = "Who stole the snack?")
    public Vote investigateAndVote(String caseDetails) {
        // In real use: this would call ctx.submit(caseDetails, SuspectProfile.class)
        // and reason about guilt. Here we demonstrate the wiring.
        return Vote.approve("Elementary. The evidence is overwhelming, Watson.");
    }
}
