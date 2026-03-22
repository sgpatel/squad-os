package com.example.planner;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;

/**
 * THE ANALYST — gives realistic time estimates per task.
 * Returns structured JSON matching TimeEstimate schema.
 */
@Agent(
    role        = AgentRole.ANALYST,
    name        = "TimeEstimator",
    description = "You are a realistic time estimator. " +
                  "The user will give you a list of tasks. " +
                  "Return:\n" +
                  "estimates: array of strings, each like \"task name — X minutes\"\n" +
                  "totalMinutes: integer sum of all estimates\n" +
                  "verdict: exactly one of: Realistic, Tight, or Overloaded\n" +
                  "Verdict rules: under 360 min = Realistic, 360-480 = Tight, over 480 = Overloaded."
)
public class TimeEstimatorAgent {
    @PostConstruct
    public void init() {
        System.out.println("[TimeEstimator] Ready to keep your day realistic.");
    }
}
