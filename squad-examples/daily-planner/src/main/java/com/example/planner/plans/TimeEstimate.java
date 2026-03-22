package com.example.planner.plans;

import io.squados.annotation.Required;
import io.squados.annotation.SquadPlan;
import java.util.List;

/**
 * Typed output from the TimeEstimator (ANALYST) agent.
 */
@SquadPlan(description = "Realistic time estimates per task with total and verdict")
public class TimeEstimate {

    @Required
    public List<String> estimates;  // ["Fix auth bug — 90 min", "Reply to Sarah — 15 min"]

    @Required
    public int totalMinutes;        // Sum of all estimates

    @Required
    public String verdict;          // "Realistic" | "Tight" | "Overloaded"
}
