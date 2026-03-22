package com.example.nodeb;
import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;

@Agent(
    role        = AgentRole.SUPPORT,
    name        = "NurseBotAgent",
    description = "You are a tactical support specialist. When given a mission, " +
                  "provide the support, logistics, and fallback plan. " +
                  "Focus on keeping the squad safe. Keep your response under 80 words."
)
public class NurseBotAgent {
    @PostConstruct
    public void init() {
        System.out.println("[NurseBotAgent] Support online — subscribed to Redis missions.");
    }
}