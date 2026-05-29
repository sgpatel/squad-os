package io.squados.dashboard.api.controller;

import io.squados.dashboard.api.model.AgentSummary;
import io.squados.dashboard.api.service.DashboardDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GET /api/v1/agents          — list all registered agents with metrics.
 * GET /api/v1/agents/{name}   — single agent detail.
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final DashboardDataService dataService;

    public AgentController(DashboardDataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping
    public ResponseEntity<List<AgentSummary>> listAgents(
            @RequestParam(name = "status", required = false) String status) {
        List<AgentSummary> agents = dataService.getAgentSummaries();
        if (status != null && !status.isBlank()) {
            agents = agents.stream()
                    .filter(a -> a.status().equalsIgnoreCase(status))
                    .toList();
        }
        return ResponseEntity.ok(agents);
    }

    @GetMapping("/{name}")
    public ResponseEntity<AgentSummary> getAgent(@PathVariable("name") String name) {
        return dataService.getAgentSummaries().stream()
                .filter(a -> a.name().equalsIgnoreCase(name))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
