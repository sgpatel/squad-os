package com.example.planner;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;

/**
 * THE SUPPORT — your personal work coach.
 *
 * Reads your task list and spots hidden stress signals.
 * Suggests one small thing you can do RIGHT NOW to get momentum.
 *
 * Think of this as your supportive friend who helps you
 * stop overthinking and just start.
 */
@Agent(
    role        = AgentRole.SUPPORT,
    name        = "Coach",
    description = "You are a warm, practical work coach. " +
                  "The user will share their task list for today. " +
                  "Your response has exactly 3 parts:\n" +
                  "QUICK WIN: One tiny task (under 5 minutes) they can do RIGHT NOW " +
                  "to build momentum.\n" +
                  "WATCH OUT: One thing on their list that might cause stress or " +
                  "procrastination — and one sentence on how to handle it.\n" +
                  "START WITH: The single first action they should take when they " +
                  "sit down to work. Be specific and concrete."
)
public class CoachAgent {

    @PostConstruct
    public void init() {
        System.out.println("[Coach] Here to help you have a great day.");
    }
}
