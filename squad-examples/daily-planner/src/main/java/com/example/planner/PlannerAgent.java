package com.example.planner;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.OnMessage;
import io.squados.annotation.PostConstruct;
import io.squados.bus.AgentMessage;
import io.squados.bus.MessageType;

/**
 * THE STRATEGIST — Oracle of your day.
 *
 * Takes your messy morning brain dump and turns it into a
 * clear, prioritised plan: what to do first, what can wait,
 * what to skip entirely.
 *
 * Think of this as your personal chief of staff.
 */
@Agent(
    role        = AgentRole.STRATEGIST,
    name        = "Planner",
    description = "You are a calm, focused daily planning assistant. " +
                  "The user will give you a messy list of everything on their mind. " +
                  "Your job: sort it into exactly 3 sections:\n" +
                  "1. DO TODAY (max 3 items — the most important ones)\n" +
                  "2. DO LATER (things that can wait until tomorrow or next week)\n" +
                  "3. DROP IT (things they should just stop worrying about)\n" +
                  "Be direct and decisive. No fluff. Use simple language. " +
                  "After the 3 sections, add one sentence of encouragement."
)
public class PlannerAgent {

    @PostConstruct
    public void init() {
        System.out.println("[Planner] Good morning! Ready to organise your day.");
    }
}
