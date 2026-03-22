package com.example.fraud.agents;

import io.squados.annotation.*;
import io.squados.vote.Vote;

/**
 * RiskAnalyst — the core fraud detection engine.
 *
 * Uses @SquadTool to query real data sources.
 * Uses @AutoPlan to iterate until confidence is high enough.
 * Uses @Eval to ensure quality before reporting.
 * Uses @Improve to learn from past correct and wrong decisions.
 * Uses @Traced for full observability.
 */
@Agent(
    role        = AgentRole.ANALYST,
    name        = "RiskAnalyst",
    description = "You are a financial fraud analyst. You analyse payment transactions " +
        "for fraud signals. You have access to transaction history, velocity checks, " +
        "and IP blocklist tools. Risk score: 0.0 = definitely safe, 1.0 = definitely fraud. " +
        "Consider: unusual amount, new merchant, different country, high velocity, " +
        "blocklisted IP, or deviation from customer spending pattern. " +
        "Always provide specific evidence for your risk score."
)
public class RiskAnalystAgent {

    @PostConstruct
    public void init() {
        System.out.println("[RiskAnalyst] Fraud detection engine online.");
    }

    @SquadTool(name = "getTransactionHistory",
               description = "Get recent transaction history for a customer")
    public String getTransactionHistory(
        @ToolParam(description = "Customer ID") String customerId,
        @ToolParam(description = "Number of days to look back") int days) {
        // Simulated transaction database
        return switch (customerId) {
            case "C-1042" -> "HISTORY[C-1042, " + days + "d]: " +
                "5 transactions avg GBP 320, usual merchants: Tesco/Amazon/Spotify, " +
                "usual country: GB, no anomalies in last 90 days";
            case "C-9999" -> "HISTORY[C-9999, " + days + "d]: " +
                "47 transactions in last 24h, avg GBP 4200, multiple countries: GB/RU/NG, " +
                "3 chargebacks in last 30 days, HIGH RISK PROFILE";
            default -> "HISTORY[" + customerId + "]: New customer, no history available";
        };
    }

    @SquadTool(name = "checkTransactionVelocity",
               description = "Check how many transactions this customer made recently")
    public String checkTransactionVelocity(
        @ToolParam(description = "Customer ID") String customerId,
        @ToolParam(description = "Time window in minutes") int minutes) {
        return switch (customerId) {
            case "C-9999" -> "VELOCITY[" + minutes + "min]: 12 transactions — EXCESSIVE (threshold: 5)";
            case "C-1042" -> "VELOCITY[" + minutes + "min]: 1 transaction — NORMAL";
            default -> "VELOCITY[" + minutes + "min]: 0 transactions — NEW CUSTOMER";
        };
    }

    @SquadTool(name = "checkIPBlocklist",
               description = "Check if an IP address is on the fraud blocklist")
    public String checkIPBlocklist(
        @ToolParam(description = "IP address to check") String ipAddress) {
        // Known bad IPs
        if (ipAddress.startsWith("185.220") || ipAddress.startsWith("91.108")) {
            return "IP_CHECK[" + ipAddress + "]: BLOCKLISTED — known Tor exit node";
        }
        if (ipAddress.startsWith("10.") || ipAddress.startsWith("192.168")) {
            return "IP_CHECK[" + ipAddress + "]: INTERNAL — corporate network";
        }
        return "IP_CHECK[" + ipAddress + "]: CLEAN — not on blocklist";
    }

    @SquadTool(name = "getMerchantRiskScore",
               description = "Get the fraud risk score for a merchant")
    public String getMerchantRiskScore(
        @ToolParam(description = "Merchant ID") String merchantId) {
        return switch (merchantId) {
            case "M-AMAZON", "M-TESCO", "M-SPOTIFY" ->
                "MERCHANT[" + merchantId + "]: riskScore=0.05 — trusted merchant";
            case "M-UNKNOWN" ->
                "MERCHANT[" + merchantId + "]: riskScore=0.75 — unverified merchant";
            default ->
                "MERCHANT[" + merchantId + "]: riskScore=0.25 — standard merchant";
        };
    }

    @AutoPlan(
        goal           = "Determine fraud risk with confidence above 0.8. " +
                         "Use all available tools. Include COMPLETE in final assessment.",
        maxIterations  = 3,
        stopCondition  = "COMPLETE",
        reflectOn      = "Which risk signals have not been checked yet? What else is needed?",
        onMaxIterations = IterationPolicy.RETURN_BEST
    )
    @Eval(
        judge       = AgentRole.CRITIC,
        minScore    = 0.75f,
        criteria    = {EvalCriteria.FAITHFULNESS, EvalCriteria.COMPLETENESS, EvalCriteria.CORRECTNESS},
        retryOnFail = true,
        maxRetries  = 2
    )
    @Improve(
        label              = "fraud-risk-assessment",
        topK               = 3,
        minExamples        = 3,
        includeNegativeExamples = true
    )
    @Traced(spanName = "risk-analyst", trackTokens = true)
    public String assessRisk(String transactionContext) {
        return transactionContext;
    }

    @SquadVote(quorum = VoteRule.MAJORITY, onTie = TieBreaker.ESCALATE,
               topic = "Is this transaction fraudulent?")
    @Traced(spanName = "risk-analyst-vote")
    public Vote castFraudVote(String riskSummary) {
        return Vote.approve("Legitimate transaction based on risk analysis");
    }
}