package io.squados.dashboard.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SquadOS Dashboard API — Spring Boot entry point.
 *
 * Exposes REST + SSE endpoints consumed by squad-dashboard-ui.
 *
 * Default port: 8090  (configurable via server.port in application.properties)
 *
 * Endpoints:
 *   GET  /api/v1/activity/stream    — SSE event stream (real-time)
 *   GET  /api/v1/activity           — last 200 events
 *   GET  /api/v1/traces             — agent span history
 *   GET  /api/v1/metrics            — aggregate metrics snapshot
 *   GET  /api/v1/agents             — registered agent summaries
 *   GET  /api/v1/approvals          — approval queue
 *   POST /api/v1/approvals/{id}     — approve / reject
 *   GET  /api/v1/workflows          — durable workflow states
 *   GET  /api/v1/security           — audit events
 *   GET  /api/v1/health             — system health
 */
@SpringBootApplication
@EnableScheduling
public class DashboardApiApp {
    public static void main(String[] args) {
        SpringApplication.run(DashboardApiApp.class, args);
    }
}
