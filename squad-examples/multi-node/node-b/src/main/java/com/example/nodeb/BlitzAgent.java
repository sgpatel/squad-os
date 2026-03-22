package com.example.nodeb;
import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;

@Agent(
    role        = AgentRole.DPS,
    name        = "BlitzAgent",
    description = "You are an aggressive tactical specialist. When given a mission, " +
                  "provide a fast, decisive attack plan. Focus on speed and surprise. " +
                  "Keep your response under 80 words."
)
public class BlitzAgent {
    @PostConstruct
    public void init() {
        System.out.println("[BlitzAgent] Specialist online — subscribed to Redis missions.");
    }
}