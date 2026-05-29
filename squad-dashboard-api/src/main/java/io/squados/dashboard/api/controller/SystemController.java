package io.squados.dashboard.api.controller;

import io.squados.dashboard.api.model.SystemHealth;
import io.squados.dashboard.api.service.DashboardDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * GET /api/v1/health — system health + JVM stats.
 */
@RestController
@RequestMapping("/api/v1")
public class SystemController {

    private final DashboardDataService dataService;

    public SystemController(DashboardDataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping("/health")
    public ResponseEntity<SystemHealth> health() {
        return ResponseEntity.ok(dataService.getSystemHealth());
    }

    /** Quick ping — returns {"status":"UP"}. */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("{\"status\":\"UP\",\"service\":\"squad-dashboard-api\",\"mode\":\""
            + (dataService.isRedisMode() ? "redis" : "simulation") + "\"}");
    }
}
