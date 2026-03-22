package com.example.fraud.plans;
import io.squados.annotation.Required;
import io.squados.annotation.SquadPlan;
import java.util.List;

/** Risk assessment produced by RiskAnalyst agent. */
@SquadPlan(description = "Transaction risk assessment")
public class RiskAssessment {
    @Required public String transactionId;
    @Required public String riskLevel;   // LOW / MEDIUM / HIGH / CRITICAL
    @Required public String riskScore;   // "0.0" to "1.0" as String
    public List<String>    riskFactors;  // what triggered the score
    public List<String>    safeFactors;  // what cleared the transaction
    public String          velocityCheck; // "3 transactions in last hour"
    public String          ipStatus;      // "CLEAN" / "BLOCKLISTED"
    public String          geoRisk;       // "LOW" / "MEDIUM" / "HIGH"
    public String          recommendation; // "APPROVE" / "REVIEW" / "BLOCK"
}