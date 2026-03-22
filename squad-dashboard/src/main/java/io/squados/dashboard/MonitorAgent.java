package io.squados.dashboard;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;

/**
 * Minimal agent required by SquadRunner to boot.
 * The dashboard is a monitoring app — this agent is a no-op placeholder.
 */
@Agent(
    role        = AgentRole.STRATEGIST,
    name        = "DashboardMonitor",
    description = "System monitor agent for the SquadOS Dashboard."
)
public class MonitorAgent {
    @PostConstruct
    public void init() {
        System.out.println("[DashboardMonitor] Monitoring agent online.");
    }
}