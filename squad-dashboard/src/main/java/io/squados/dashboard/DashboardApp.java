package io.squados.dashboard;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;
import io.squados.annotation.SquadApplication;
import io.squados.trace.RedisTraceExporter;
import io.squados.trace.SquadTracer;
import io.squados.approval.InProcessApprovalStore;
import io.squados.improve.InProcessFeedbackStore;
import io.squados.security.AuditLog;
import io.squados.security.SecurityGuard;
import io.squados.context.SquadContext;
import io.squados.vote.*;
import io.squados.annotation.VoteRule;
import io.squados.annotation.TieBreaker;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * SquadOS Dashboard — Live monitoring for agent squads.
 *
 * Open http://localhost:8080 to view the dashboard.
 * API: http://localhost:8080/api/status
 */
@SpringBootApplication
@SquadApplication
public class DashboardApp {

    @Bean public io.squados.trace.RedisTraceExporter traceExporter() {
        String host = System.getProperty("redis.host", "localhost");
        int port = Integer.parseInt(System.getProperty("redis.port", "6379"));
        io.squados.trace.RedisTraceExporter exp = new io.squados.trace.RedisTraceExporter(host, port);
        io.squados.trace.SquadTracer.configure(exp);
        return exp;
    }
    @Bean public InProcessApprovalStore approvalStore() { return new InProcessApprovalStore(); }
    @Bean public InProcessFeedbackStore feedbackStore() { return new InProcessFeedbackStore(); }
    @Bean public AuditLog auditLog() { return new AuditLog(); }
    @Bean public SecurityGuard securityGuard(AuditLog log) { return new SecurityGuard(log); }

    public static void main(String[] args) {
        SpringApplication.run(DashboardApp.class, args);
    }

    @Bean
    public ApplicationRunner runner(SquadContext ctx,
                                    DashboardState dashState) {
        return args -> {
            System.out.println();
            System.out.println("  ╔════════════════════════════════════════════╗");
            System.out.println("  ║  🛡️  SquadOS Dashboard v3.3.0               ║");
            System.out.println("  ║  Live monitoring for AI agent squads      ║");
            System.out.println("  ╚════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("  Dashboard: http://localhost:8080");
            System.out.println("  API:       http://localhost:8080/api/status");
            System.out.println();

            // Seed some demo data
            dashState.recordActivity("AGENT", "GatewayAgent", "Payment gateway online");
            dashState.recordActivity("AGENT", "RiskAnalyst",  "Fraud detection engine online");
            dashState.recordActivity("SYSTEM", "SquadOS",     "Squad ready — " + ctx.getRegistry().all().size() + " agents registered");

            // Seed a demo vote result
            VoteCollector col = new VoteCollector("demo-fraud-check",
                VoteRule.MAJORITY, TieBreaker.ESCALATE, 3, 5);
            col.submit(Vote.approve("Risk score 0.18 — clean", 1.0).withVoter("RiskAnalyst"), "RiskAnalyst");
            col.submit(Vote.approve("Behaviour normal", 1.0).withVoter("BehaviourAgent"), "BehaviourAgent");
            col.submit(Vote.approve("AML checks passed", 1.0).withVoter("ComplianceAgent"), "ComplianceAgent");
            try {
                VoteResult result = col.resolve();
                dashState.recordVote(result);
                dashState.recordActivity("VOTE", "Squad", "Vote result: " + result.getOutcome() + " (3-0)");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}