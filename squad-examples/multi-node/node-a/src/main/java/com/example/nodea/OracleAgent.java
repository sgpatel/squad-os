package com.example.nodea;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;

/**
 * Oracle — the Strategist on Node A.
 * Receives missions from the user, delegates to specialists on Node B via Redis.
 */
@Agent(
    role        = AgentRole.STRATEGIST,
    name        = "Oracle",
    description = "You are a tactical commander. You receive mission briefs and " +
                  "coordinate your squad. Be concise and strategic."
)
public class OracleAgent {

    @PostConstruct
    public void init() {
        System.out.println("[Oracle] Commander online — publishing tasks to squad via Redis.");
    }
}
