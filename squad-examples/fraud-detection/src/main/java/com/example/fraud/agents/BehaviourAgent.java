package com.example.fraud.agents;

import io.squados.annotation.*;
import io.squados.vote.Vote;

/**
 * BehaviourAgent — analyses customer behaviour patterns.
 *
 * Uses @Memory to remember past customer behaviour across sessions.
 * Uses @SquadTool for geo-risk and device fingerprint checks.
 * Votes in the @SquadVote fraud determination.
 */
@Agent(
    role        = AgentRole.RESEARCHER,
    name        = "BehaviourAgent",
    description = "You are a customer behaviour analyst specialising in fraud detection. " +
        "You analyse whether a transaction fits the customer behaviour profile. " +
        "Check: Is this a typical purchase category? Typical time of day? Known device? " +
        "Typical geographic location? Usual spending amount? " +
        "You have access to geo-risk data and device fingerprints. " +
        "A transaction is suspicious if it deviates significantly from past behaviour."
)
@MissionProfile("work")
public class BehaviourAgent {

    @PostConstruct
    public void init() {
        System.out.println("[BehaviourAgent] Behaviour analysis engine online. Memory enabled.");
    }

    @SquadTool(name = "getGeoRisk",
               description = "Get the fraud risk level for a country or region")
    public String getGeoRisk(
        @ToolParam(description = "Country code e.g. GB, US, NG") String countryCode) {
        return switch (countryCode.toUpperCase()) {
            case "GB", "US", "DE", "FR", "CA", "AU" ->
                "GEO_RISK[" + countryCode + "]: LOW — trusted region";
            case "NG", "GH", "KE" ->
                "GEO_RISK[" + countryCode + "]: HIGH — elevated fraud region";
            case "RU", "UA", "BY" ->
                "GEO_RISK[" + countryCode + "]: MEDIUM — monitor closely";
            default ->
                "GEO_RISK[" + countryCode + "]: MEDIUM — unknown region";
        };
    }

    @SquadTool(name = "checkDeviceFingerprint",
               description = "Check if the device fingerprint matches customer known devices")
    public String checkDeviceFingerprint(
        @ToolParam(description = "Customer ID") String customerId,
        @ToolParam(description = "Device fingerprint hash") String fingerprint) {
        // Simulated device registry
        if (fingerprint.startsWith("KNOWN-")) {
            return "DEVICE[" + fingerprint + "]: RECOGNISED — customer known device";
        }
        if (fingerprint.startsWith("NEW-")) {
            return "DEVICE[" + fingerprint + "]: NEW DEVICE — first seen today, moderate risk";
        }
        return "DEVICE[" + fingerprint + "]: UNKNOWN — not registered, elevated risk";
    }

    @OnMessage(from = AgentRole.STRATEGIST, type = io.squados.bus.MessageType.DIRECTIVE)
    public void onGatewayDirective(io.squados.bus.AgentMessage msg) {
        System.out.println("[BehaviourAgent] Received directive from GatewayAgent: " + String.valueOf(msg.getPayload()));
    }

    @Traced(spanName = "behaviour-vote")
    public Vote castBehaviourVote(String behaviourSummary) {
        return Vote.approve("Behaviour pattern consistent with customer profile");
    }
}