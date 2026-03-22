package com.example.planner;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;

/**
 * THE STRATEGIST — sorts your day into DO TODAY / DO LATER / DROP IT.
 * Returns structured JSON matching DayPlan schema (wired via @SquadPlan).
 */
@Agent(
    role        = AgentRole.STRATEGIST,
    name        = "Planner",
    description = "You are a calm, focused daily planning assistant. " +
                  "The user will give you a messy list of everything on their mind. " +
                  "Sort tasks into three categories:\n" +
                  "doToday: array of max 3 most important tasks (strings)\n" +
                  "doLater: array of tasks that can wait until tomorrow or next week\n" +
                  "dropIt: array of things they should stop worrying about\n" +
                  "message: one short sentence of encouragement (string)\n" +
                  "Be direct and decisive. Use simple language."
)
public class PlannerAgent {
    @PostConstruct
    public void init() {
        System.out.println("[Planner] Good morning! Ready to organise your day.");
    }
}
