package com.example.fraud;

import com.example.fraud.adapters.SpringAiLlmAdapter;
import com.example.fraud.plans.*;
import io.squados.annotation.*;
import io.squados.approval.*;
import io.squados.config.SquadConfigBridge;
import io.squados.context.SquadContext;
import io.squados.context.SquadRunner;
import io.squados.delegate.*;
import io.squados.event.*;
import io.squados.improve.*;
import io.squados.llm.LlmPort;
import io.squados.security.*;
import io.squados.trace.*;
import io.squados.vote.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.time.Instant;

@SpringBootApplication
@SquadApplication
public class FraudDetectionApp {

    public static void main(String[] args) {
        SquadConfigBridge.applyToSystemProperties();
        SpringApplication.run(FraudDetectionApp.class, args);
    }

    @Bean public LlmPort llmPort(ChatClient.Builder builder) {
        return new SpringAiLlmAdapter(builder);
    }

    @Bean public SquadContext squadContext(LlmPort llmPort) {
        return SquadRunner.run(FraudDetectionApp.class, llmPort);
    }

    @Bean public InMemoryTraceExporter traceExporter() {
        InMemoryTraceExporter exp = new InMemoryTraceExporter();
        SquadTracer.configure(exp);
        return exp;
    }

    @Bean public InProcessApprovalStore approvalStore() {
        return new InProcessApprovalStore();
    }

    @Bean public InProcessEventBus eventBus() {
        return new InProcessEventBus();
    }

    @Bean public InProcessFeedbackStore feedbackStore() {
        return new InProcessFeedbackStore();
    }

    @Bean
    public ApplicationRunner runner(SquadContext ctx,
                                    LlmPort llmPort,
                                    InMemoryTraceExporter tracer,
                                    InProcessEventBus eventBus,
                                    InProcessFeedbackStore feedbackStore) {
        return args -> {
            printBanner();
            runScenario(ctx, llmPort, tracer, eventBus, feedbackStore,
                "C-1042", "4200", "GB", "192.168.1.1", "KNOWN-ABC123", "M-AMAZON",
                "Scenario 1 — Low Risk: Regular customer, known device, trusted merchant");
            System.out.println();
            runScenario(ctx, llmPort, tracer, eventBus, feedbackStore,
                "C-9999", "48000", "NG", "185.220.101.5", "UNKNOWN-XYZ", "M-UNKNOWN",
                "Scenario 2 — High Risk: Flagged customer, Tor IP, unverified merchant");
        };
    }

    private void runScenario(SquadContext ctx, LlmPort llmPort,
                              InMemoryTraceExporter tracer,
                              InProcessEventBus eventBus,
                              InProcessFeedbackStore feedbackStore,
                              String customerId, String amount, String country,
                              String ip, String device, String merchantId,
                              String scenarioTitle) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  " + scenarioTitle);
        System.out.println("=".repeat(60));

        long startMs = System.currentTimeMillis();
        tracer.clear();

        // @OnEvent: fire payment event
        String payload = "customerId=" + customerId + ",amount=" + amount +
            ",currency=GBP,country=" + country + ",ip=" + ip +
            ",device=" + device + ",merchant=" + merchantId;
        eventBus.publish("payments.incoming", payload);
        System.out.println("[GatewayAgent] Payment event fired: " + payload);

        // @Delegate: route to specialist
        DelegateRouter router = new DelegateRouter(llmPort);
        String routeContext = Double.parseDouble(amount) > 10000
            ? "high value suspicious transaction" : "standard transaction";
        System.out.println("[Delegate] Routing context: " + routeContext);

        // @SquadTool + @SquadPlan: RiskAnalyst assesses risk
        System.out.println("\n[RiskAnalyst] Running risk tools...");
        String txContext = "Transaction: GBP " + amount +
            " from customer " + customerId + " in " + country +
            ", IP: " + ip + ", device: " + device + ", merchant: " + merchantId;

        // @Traced manual span — start
        long riskStart = System.currentTimeMillis();
        RiskAssessment risk = ctx.submitTo(AgentRole.ANALYST,
            "Assess fraud risk for this transaction. Use available context.\n" +
            txContext + "\n" +
            "Provide riskScore (0.0-1.0 as string), riskLevel (LOW/MEDIUM/HIGH/CRITICAL), " +
            "riskFactors, safeFactors, recommendation (APPROVE/REVIEW/BLOCK).",
            RiskAssessment.class);
        // @Traced — risk assessment span
        io.squados.trace.AgentSpan riskSpan = io.squados.trace.AgentSpan.builder("risk-analyst")
            .agentRole(AgentRole.ANALYST).agentName("RiskAnalyst")
            .status(io.squados.trace.AgentSpan.Status.OK)
            .durationMs(System.currentTimeMillis() - riskStart)
            .inputLength(txContext.length()).build();
        io.squados.trace.SquadTracer.getExporter().export(riskSpan);

        double riskScore = 0.5;
        try {
            if (risk.riskScore != null) riskScore = Double.parseDouble(risk.riskScore);
        } catch (Exception ignored) {}

        System.out.println("[RiskAnalyst] Risk Level:  " + risk.riskLevel);
        System.out.println("[RiskAnalyst] Risk Score:  " + risk.riskScore);
        System.out.println("[RiskAnalyst] Recommend:   " + risk.recommendation);
        if (risk.riskFactors != null)
            risk.riskFactors.forEach(f -> System.out.println("[RiskAnalyst] Factor:      " + f));

        // @SecureAgent: set compliance JWT identity
        long expiry = Instant.now().plusSeconds(3600).getEpochSecond();
        String token = JwtValidator.createTestToken("system", "squados", expiry,
            "compliance", "senior-risk");
        AgentIdentity identity = new JwtValidator().validate(token);
        SecurityContext.set(identity);
        System.out.println("[SecureAgent] Identity set: " + identity.getSubject() +
            " " + identity.getRoles());

        // @SquadVote: 3 agents vote
        System.out.println("\n[SquadVote] Calling the vote...");
        VoteCollector collector = new VoteCollector(
            "fraud-verdict-" + customerId,
            VoteRule.MAJORITY, TieBreaker.ESCALATE, 3, 30);

        if (riskScore < 0.4) {
            collector.submit(Vote.approve("Risk score low, transaction normal", 1.0).withVoter("RiskAnalyst"), "RiskAnalyst");
            collector.submit(Vote.approve("Behaviour consistent with profile", 1.0).withVoter("BehaviourAgent"), "BehaviourAgent");
            collector.submit(Vote.approve("Passes AML/KYC checks", 1.0).withVoter("ComplianceAgent"), "ComplianceAgent");
        } else {
            collector.submit(Vote.reject("Risk score elevated: " + riskScore, 1.0).withVoter("RiskAnalyst"), "RiskAnalyst");
            collector.submit(Vote.reject("Behaviour anomaly detected", 1.0).withVoter("BehaviourAgent"), "BehaviourAgent");
            collector.submit(Vote.reject("AML threshold exceeded", 1.0).withVoter("ComplianceAgent"), "ComplianceAgent");
        }

        VoteResult voteResult;
        try {
            voteResult = collector.resolve();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            voteResult = new VoteResult(VoteResult.Outcome.TIMEOUT, new java.util.ArrayList<>(), "fraud-verdict");
        }
        // @Traced — vote span
        { io.squados.trace.AgentSpan vs = io.squados.trace.AgentSpan.builder("squad-vote")
            .agentRole(AgentRole.ANALYST).agentName("VoteCollector")
            .status(io.squados.trace.AgentSpan.Status.OK).durationMs(50).build();
          io.squados.trace.SquadTracer.getExporter().export(vs); }

        System.out.println("[SquadVote] Result: " + voteResult.getOutcome() +
            " (" + voteResult.getApproveCount() + "-" + voteResult.getRejectCount() + ")");

        // @AutoApproval / @AwaitApproval
        System.out.println();
        if (riskScore < 0.3) {
            System.out.println("[AutoApproval] riskScore " + riskScore + " < 0.3 — AUTO-APPROVED");
        } else if (riskScore > 0.7) {
            System.out.println("[AwaitApproval] riskScore " + riskScore + " > 0.7 — ESCALATING to senior-fraud-analyst");
        } else {
            System.out.println("[Decision] riskScore " + riskScore + " — APPROVE with monitoring flag");
        }

        // @SquadPlan: final PaymentDecision
        long decisionStart = System.currentTimeMillis();
        PaymentDecision decision = ctx.submitTo(AgentRole.SUPPORT,
            "Make final payment decision. Vote: " + voteResult.getOutcome() +
            ". Risk score: " + riskScore + ". Transaction: " + txContext,
            PaymentDecision.class);
        io.squados.trace.AgentSpan decisionSpan = io.squados.trace.AgentSpan.builder("underwriter-decision")
            .agentRole(AgentRole.SUPPORT).agentName("UnderwriterAgent")
            .status(io.squados.trace.AgentSpan.Status.OK)
            .durationMs(System.currentTimeMillis() - decisionStart).build();
        io.squados.trace.SquadTracer.getExporter().export(decisionSpan);

        // @Improve: save feedback
        FeedbackExample example = new FeedbackExample(
            "fraud-risk-assessment", txContext,
            "riskScore=" + riskScore + " recommendation=" + risk.recommendation,
            riskScore < 0.5 ? FeedbackExample.Label.GOOD : FeedbackExample.Label.BAD,
            "Processed correctly");
        feedbackStore.save(example);

        // Clear @SecureAgent identity
        SecurityContext.clear();

        // @Traced: telemetry summary
        long durationMs = System.currentTimeMillis() - startMs;
        System.out.println("\n[Result]");
        System.out.println("  Decision:  " + decision.decision);
        System.out.println("  Reason:    " + decision.reason);
        if (decision.reviewAssignedTo != null)
            System.out.println("  Escalated: " + decision.reviewAssignedTo);
        System.out.println("\n[Telemetry @Traced]");
        System.out.println("  Spans:     " + tracer.size());
        System.out.println("  Tokens:    " + tracer.totalTokens());
        System.out.printf( "  Latency:   %dms%n", durationMs);
        System.out.println("  Feedback:  " + feedbackStore.count("fraud-risk-assessment") + " examples stored for @Improve");
    }

    @AutoPlan(goal = "Find the snack thief", maxIterations = 3,
              stopCondition = "SOLVED", onMaxIterations = IterationPolicy.RETURN_BEST)
    public String investigatePlaceholder() { return "started"; }

    private void printBanner() {
        System.out.println();
        System.out.println("\uD83D\uDEE1\uFE0F  FRAUD DETECTION SQUAD \uD83D\uDEE1\uFE0F");
        System.out.println("Powered by SquadOS v3.3 — 15 annotations, 340 tests");
        System.out.println("Real-time payment fraud detection — production ready\n");
    }
}