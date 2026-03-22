package com.example.snackthief.agents;

import io.squados.annotation.*;

import io.squados.vote.*;


/**
 * JudgeJudy — The CRITIC agent.
 * Zero tolerance. Has seen it all. Tired of excuses.
 * Her word is final. There are no appeals.
 */
@Agent(
    role        = AgentRole.CRITIC,
    name        = "JudgeJudy",
    description = "You are JudgeJudy, an AI modelled after the legendary no-nonsense TV judge. " +
                  "You have ZERO patience for excuses. You cut to the chase. " +
                  "You say 'Don't pee on my leg and tell me it's raining' at least once. " +
                  "You call the accused 'sir' or 'ma'am' with increasing contempt. " +
                  "Your sentences always involve some form of public humiliation PLUS replacement food. " +
                  "There are NO appeals in your court. You are final. You are righteous. " +
                  "You are JudgeJudy and you are NOT here for the nonsense."
)
public class JudgeJudyAgent {

    @PostConstruct
    public void init() {
        System.out.println("[JudgeJudy] I've been on the bench for 25 years. " +
            "I have seen parking disputes, inheritance battles, and small claims for $400. " +
            "But pizza theft? That is a new low. Let's BEGIN.");
    }

    @Eval(
        judge       = AgentRole.CRITIC,
        minScore    = 0.85f,
        criteria    = {EvalCriteria.CORRECTNESS, EvalCriteria.COMPLETENESS},
        retryOnFail = true,
        maxRetries  = 2
    )
    @Traced(spanName = "judgejudy-verdict")
    public String deliverVerdict(String caseFile) {
        return "COURT IS IN SESSION. Based on the evidence presented, " +
               "this court finds the accused GUILTY. Don't pee on my leg " +
               "and tell me it's raining, ma'am. DISMISSED.";
    }
}
