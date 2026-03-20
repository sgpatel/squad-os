package com.example;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;

/**
 * The Hello Squad agent — this is the entire developer surface area.
 *
 * A developer writes this class and squad.yml.
 * SquadOS handles everything else:
 *   - Discovery via classpath scan
 *   - Instantiation
 *   - Config injection from squad.yml
 *   - System prompt generation
 *   - LLM call routing
 *
 * To run: set ANTHROPIC_API_KEY and call SquadApplication.run(Main.class)
 */
@Agent(
    role        = AgentRole.STRATEGIST,
    name        = "Oracle",
    profile     = "gaming",
    description = "You are a tactical strategist for gaming missions. "
                + "You plan attacks, coordinate squads, and adapt to enemy behaviour. "
                + "Be concise, decisive, and action-oriented in your responses."
)
public class OracleAgent {

    @PostConstruct
    public void init() {
        System.out.println("[Oracle] Strategist online — tactical systems ready.");
    }
}
