package io.squados.dashboard.api.controller;

import io.squados.dashboard.api.model.MetricsSummary;
import io.squados.dashboard.api.service.DashboardDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * GET /api/v1/metrics — aggregate metrics snapshot.
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final DashboardDataService dataService;

    public MetricsController(DashboardDataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping
    public ResponseEntity<MetricsSummary> getMetrics() {
        return ResponseEntity.ok(dataService.getMetrics());
    }
}
