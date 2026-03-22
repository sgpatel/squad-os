package com.example.fraud.agents;

import io.squados.annotation.*;
import io.squados.vote.Vote;

/**
 * ComplianceAgent — enforces regulatory compliance.
 *
 * Uses @SecureAgent — only callers with "compliance" JWT role can invoke.
 * Uses @Eval to quality-gate its own compliance reports.
 * Votes in the @SquadVote fraud determination.
 */
@Agent(
    role        = AgentRole.CRITIC,
    name        = "ComplianceAgent",
    description = "You are a financial compliance officer AI. You review transactions " +
        "against AML (Anti-Money Laundering) and KYC (Know Your Customer) rules. " +
        "You check: Does this transaction trigger AML thresholds? " +
        "Is the customer KYC-verified? Any PEP (Politically Exposed Person) flags? " +
        "Does the transaction pattern suggest layering or smurfing? " +
        "You are conservative — when in doubt, flag for human review. " +
        "Always cite the specific regulation or rule that applies."
)
public class ComplianceAgent {

    @PostConstruct
    public void init() {
        System.out.println("[ComplianceAgent] Compliance engine online. AML/KYC rules loaded.");
    }

    @SecureAgent(
        roles      = {"compliance", "senior-risk"},
        auditLog   = true,
        denyMessage = "Compliance checks require compliance officer credentials"
    )
    @Eval(
        judge       = AgentRole.CRITIC,
        minScore    = 0.8f,
        criteria    = {EvalCriteria.FAITHFULNESS, EvalCriteria.CORRECTNESS},
        retryOnFail = true,
        maxRetries  = 2
    )
    @Traced(spanName = "compliance-check", trackTokens = true)
    public String performComplianceCheck(String transactionContext) {
        return transactionContext;
    }

    @SecureAgent(mode = AccessMode.PUBLIC, auditLog = true)
    @Traced(spanName = "compliance-vote")
    public Vote castComplianceVote(String complianceSummary) {
        return Vote.approve("Transaction passes AML/KYC compliance checks");
    }
}