package com.example.fraud.agents;

import io.squados.annotation.*;
import io.squados.event.SquadEvent;

/**
 * GatewayAgent — the entry point for all incoming payments.
 *
 * Triggered by @OnEvent on the payments.incoming topic.
 * Uses @Delegate to route to the right specialist based on payment type.
 * High-value payments go to ANALYST, behaviour checks to RESEARCHER.
 */
@Agent(
    role        = AgentRole.STRATEGIST,
    name        = "GatewayAgent",
    description = "You are a payment gateway agent. Your job is to triage incoming " +
        "payment events and route them to the right specialist. " +
        "For high-value or suspicious transactions, route to ANALYST. " +
        "For pattern analysis or geo checks, route to RESEARCHER. " +
        "Always be concise and structured in your responses."
)
public class GatewayAgent {

    @PostConstruct
    public void init() {
        System.out.println("[GatewayAgent] Payment gateway online. Monitoring transactions...");
    }

    @OnEvent(
        topic       = "payments.incoming",
        filter      = "amount > 0",
        concurrency = 5,
        retryOnError = true,
        maxRetries  = 2
    )
    public void onPayment(SquadEvent event) {
        System.out.printf("[GatewayAgent] Payment received: %s%n", event.getPayload());
    }

    @Delegate(
        candidates  = {AgentRole.ANALYST, AgentRole.RESEARCHER},
        strategy    = DelegateStrategy.FIRST_MATCH,
        conditions  = {"high OR suspicious OR large OR critical", "pattern OR geo OR behaviour OR velocity"},
        fallback    = AgentRole.ANALYST,
        logDecision = true
    )
    public String routeToSpecialist(String paymentContext) {
        return paymentContext;
    }
}