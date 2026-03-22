package com.example.planner.plans;

import io.squados.annotation.Required;
import io.squados.annotation.SquadPlan;

/**
 * Typed output from the Coach (SUPPORT) agent.
 */
@SquadPlan(description = "Coaching advice: quick win, watch out, and start with")
public class CoachAdvice {

    @Required
    public String quickWin;   // One tiny task under 5 minutes to build momentum

    @Required
    public String watchOut;   // One thing that might cause stress + how to handle it

    @Required
    public String startWith;  // The single first concrete action to take
}
