package io.squados.dashboard.api.controller;

import io.squados.dashboard.api.model.TraceSpan;
import io.squados.dashboard.api.service.DashboardDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GET /api/v1/traces           — recent agent spans (newest first).
 * GET /api/v1/traces/{traceId} — single trace by ID.
 */
@RestController
@RequestMapping("/api/v1/traces")
public class TraceController {

    private final DashboardDataService dataService;

    public TraceController(DashboardDataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping
    public ResponseEntity<List<TraceSpan>> listTraces(
            @RequestParam(name = "limit", defaultValue = "200") int limit,
            @RequestParam(name = "agent", required = false) String agent,
            @RequestParam(name = "status", required = false) String status) {
        List<TraceSpan> spans = dataService.getTraces(limit);
        if (agent != null && !agent.isBlank()) {
            spans = spans.stream()
                    .filter(s -> s.agentName().equalsIgnoreCase(agent))
                    .toList();
        }
        if (status != null && !status.isBlank()) {
            spans = spans.stream()
                    .filter(s -> s.status().equalsIgnoreCase(status))
                    .toList();
        }
        return ResponseEntity.ok(spans);
    }

    @GetMapping("/{traceId}")
    public ResponseEntity<TraceSpan> getTrace(@PathVariable("traceId") String traceId) {
        return dataService.getTraces(500).stream()
                .filter(s -> s.traceId().equals(traceId))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
