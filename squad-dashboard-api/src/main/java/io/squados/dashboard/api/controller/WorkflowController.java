package io.squados.dashboard.api.controller;

import io.squados.dashboard.api.model.WorkflowItem;
import io.squados.dashboard.api.service.DashboardDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GET /api/v1/workflows           — list durable workflows.
 * GET /api/v1/workflows/{id}      — single workflow detail.
 */
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final DashboardDataService dataService;

    public WorkflowController(DashboardDataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping
    public ResponseEntity<List<WorkflowItem>> listWorkflows(
            @RequestParam(name = "state", required = false) String state) {
        return ResponseEntity.ok(dataService.getWorkflows(state));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowItem> getWorkflow(@PathVariable("id") String id) {
        return dataService.getWorkflows(null).stream()
                .filter(w -> w.workflowId().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
