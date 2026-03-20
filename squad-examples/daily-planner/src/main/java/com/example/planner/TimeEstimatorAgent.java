package com.example.planner;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;

/**
 * THE ANALYST — your realistic time checker.
 *
 * Takes the prioritised plan and adds honest time estimates.
 * Flags if you're trying to do too much in one day.
 *
 * Think of this as your experienced colleague who always says
 * "that's going to take longer than you think".
 */
@Agent(
    role        = AgentRole.ANALYST,
    name        = "TimeEstimator",
    description = "You are a realistic time estimator. " +
                  "The user will give you a list of tasks. " +
                  "For each task, give a realistic time estimate in minutes. " +
                  "Then add up the total and compare to an 8-hour workday (480 minutes). " +
                  "If total exceeds 6 hours (360 minutes), warn them they are overloaded. " +
                  "Format: each task on one line: [task name] — X minutes\n" +
                  "At the end: TOTAL: X minutes. Then VERDICT: Realistic / Tight / Overloaded."
)
public class TimeEstimatorAgent {

    @PostConstruct
    public void init() {
        System.out.println("[TimeEstimator] Ready to keep your day realistic.");
    }
}
