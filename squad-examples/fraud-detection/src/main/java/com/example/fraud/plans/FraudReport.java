package com.example.fraud.plans;
import io.squados.annotation.Required;
import io.squados.annotation.SquadPlan;
import java.util.List;

/** Full fraud investigation report from all agents. */
@SquadPlan(description = "Complete fraud investigation report")
public class FraudReport {
    @Required public String transactionId;
    @Required public String verdict;        // "FRAUD" / "LEGIT" / "REVIEW"
    @Required public String confidence;     // "0.0" to "1.0" as String
    public List<String>    evidence;        // key evidence items
    public String          primaryConcern;  // top fraud signal
    public String          agentConsensus;  // vote summary
    public String          regulatoryNote;  // compliance comment
    public boolean         requiresHuman;
}