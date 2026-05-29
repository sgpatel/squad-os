package io.squados.dashboard.api.controller;

import io.squados.dashboard.api.model.SecurityEvent;
import io.squados.dashboard.api.service.DashboardDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GET /api/v1/security — recent security / audit events.
 */
@RestController
@RequestMapping("/api/v1/security")
public class SecurityController {

    private final DashboardDataService dataService;

    public SecurityController(DashboardDataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping
    public ResponseEntity<List<SecurityEvent>> listEvents(
            @RequestParam(name = "limit", defaultValue = "100") int limit,
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "type", required = false) String type) {
        List<SecurityEvent> events = dataService.getSecurityEvents(limit);
        if (severity != null && !severity.isBlank()) {
            events = events.stream()
                    .filter(e -> e.severity().equalsIgnoreCase(severity))
                    .toList();
        }
        if (type != null && !type.isBlank()) {
            events = events.stream()
                    .filter(e -> e.type().equalsIgnoreCase(type))
                    .toList();
        }
        return ResponseEntity.ok(events);
    }
}
