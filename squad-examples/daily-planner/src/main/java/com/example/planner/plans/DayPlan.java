package com.example.planner.plans;

import io.squados.annotation.Required;
import io.squados.annotation.SquadPlan;
import java.util.List;

/**
 * Typed output from the Planner (STRATEGIST) agent.
 * Replaces the raw string response with a structured Java object.
 *
 * Before @SquadPlan:
 *   String plan = result.get(AgentRole.STRATEGIST).content();
 *   // "DO TODAY:\n1. Fix auth bug\n2. Reply to Sarah..."
 *
 * After @SquadPlan:
 *   DayPlan plan = ctx.submitTo(AgentRole.STRATEGIST, input, DayPlan.class);
 *   plan.doToday   // ["Fix auth bug", "Reply to Sarah"]
 *   plan.dropIt    // ["Learn Kubernetes", "Organise desk"]
 *   plan.message   // "You've got this!"
 */
@SquadPlan(description = "Daily task prioritisation — sorted into DO TODAY, DO LATER, DROP IT")
public class DayPlan {

    @Required
    public List<String> doToday;   // Max 3 items — highest priority

    public List<String> doLater;   // Can wait until tomorrow or next week

    public List<String> dropIt;    // Stop worrying about these

    public String message;          // One sentence of encouragement
}
