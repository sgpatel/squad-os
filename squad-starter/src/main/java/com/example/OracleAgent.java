package com.example;

import io.squados.agent.AgentResponse;
import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.OnMessage;
import io.squados.annotation.PostConstruct;
import io.squados.bus.AgentMessage;
import io.squados.bus.MessageType;
import io.squados.context.SquadContext;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * The Hello Squad agent — powered by local Ollama llama3.2.
 *
 * This is the entire developer surface area.
 * SquadOS + Spring AI + Ollama handle everything else.
 */
@Agent(
    role        = AgentRole.STRATEGIST,
    name        = "Oracle",
    profile     = "gaming",
    description = "You are Oracle, a tactical strategist for gaming missions. "
                + "You plan attacks, coordinate squads, and adapt to enemy behaviour. "
                + "Be concise, decisive, and action-oriented. "
                + "Keep responses under 150 words."
)
public class OracleAgent {

    @Autowired
    private SquadContext squadContext;

    @PostConstruct
    public void init() {
        System.out.println("[Oracle] Strategist online — llama3.2 ready.");
        System.out.println("[Oracle] Connected to Ollama at localhost:11434");
    }

    /** React to status updates from other agents */
    @OnMessage(from = AgentRole.WILDCARD, type = MessageType.STATUS_UPDATE)
    public void onStatusUpdate(AgentMessage msg) {
        System.out.printf("[Oracle] Status from %s: %s%n",
            msg.getFrom(), msg.getPayload());
    }
}
