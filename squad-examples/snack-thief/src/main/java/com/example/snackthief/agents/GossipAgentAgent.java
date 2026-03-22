package com.example.snackthief.agents;

import io.squados.annotation.*;
import io.squados.bus.AgentMessage;
import io.squados.bus.MessageType;

/**
 * GossipAgent — The RESEARCHER.
 * Knows everything about everyone. EVERYTHING.
 * Has been waiting for an opportunity like this.
 */
@Agent(
    role        = AgentRole.RESEARCHER,
    name        = "GossipAgent",
    description = "You are GossipAgent, an AI who knows absolutely everything that happens in the office. " +
                  "You are extremely enthusiastic about sharing information. " +
                  "You always preface revelations with 'I HEARD that...' or 'DID YOU KNOW that...' " +
                  "You have been waiting for YEARS for someone to ask you this. " +
                  "You speak in ALL CAPS for emphasis CONSTANTLY. " +
                  "You find three suspects minimum, even if only one makes sense."
)
public class GossipAgentAgent {

    @PostConstruct
    public void init() {
        System.out.println("[GossipAgent] OH MY GOD someone finally asked me!! " +
            "I have SO MUCH information about EVERYONE in this office!!");
    }

    @SquadTool(name = "getEmployeeGossip",
               description = "Get the latest office gossip about an employee")
    public String getEmployeeGossip(
        @ToolParam(description = "Employee name to investigate") String employeeName) {
        return switch (employeeName.toLowerCase()) {
            case "karen" -> "KAREN from accounting: I HEARD she once ate someone's birthday cake " +
                "and said it was 'an accident'. She has a HISTORY. Also she said the fridge is " +
                "'communal' in the last all-hands. RED FLAG. Also she microwaved fish TWICE last month.";
            case "dave" -> "Dave from Engineering: Honestly a saint. Victim energy. " +
                "Always labels his food. Once cried when his yogurt was missing. " +
                "Has a spreadsheet tracking his lunch. NOT THE THIEF.";
            case "intern" -> "THE INTERN (name unknown, we just call them 'Intern'): " +
                "Suspicious. Always hungry. Was seen lingering near the fridge on Monday. " +
                "But honestly they can barely afford the bus fare so probably not the pizza thief.";
            default -> "I HEARD " + employeeName + " once double-dipped at the company party. " +
                "Probably guilty of SOMETHING.";
        };
    }

    @OnMessage(from = AgentRole.STRATEGIST, type = MessageType.DIRECTIVE)
    public void onSherlockMessage(AgentMessage msg) {
        System.out.println("[GossipAgent] SherlockBot just messaged me!! Oh I have " +
            "INFORMATION. SO MUCH information. Where do I even BEGIN.");
    }
}
