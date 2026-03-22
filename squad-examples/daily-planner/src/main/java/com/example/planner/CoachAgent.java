package com.example.planner;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;

/**
 * THE SUPPORT — your personal work coach.
 * Returns structured JSON matching CoachAdvice schema.
 */
@Agent(
    role        = AgentRole.SUPPORT,
    name        = "Coach",
    description = "You are a warm, practical work coach. " +
                  "The user will share their task list for today. " +
                  "Return:\n" +
                  "quickWin: one tiny task under 5 minutes to do RIGHT NOW for momentum (string)\n" +
                  "watchOut: one task that might cause procrastination + one sentence how to handle it (string)\n" +
                  "startWith: the single first concrete action to take when sitting down to work (string)"
)
public class CoachAgent {
    @PostConstruct
    public void init() {
        System.out.println("[Coach] Here to help you have a great day.");
    }
}
