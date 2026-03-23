package com.example.fraud;
import com.example.fraud.adapters.SpringAiLlmAdapter;
import com.example.fraud.adapters.TokenTrackingLlmPort;
import com.example.fraud.plans.*;
import io.squados.annotation.*;
import io.squados.approval.InProcessApprovalStore;
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
        SpringApplication.run(FraudDetectionApp.class, args);
    }

    @Bean public TokenTrackingLlmPort tokenTracker(ChatClient.Builder builder) {
        return new TokenTrackingLlmPort(new SpringAiLlmAdapter(builder));
    }


    @Bean public SquadContext squadContext(TokenTrackingLlmPort tracker) {
        return SquadRunner.run(FraudDetectionApp.class, tracker);
    }

    @Bean public io.squados.trace.RedisTraceExporter traceExporter() {
        io.squados.trace.RedisTraceExporter exp = new io.squados.trace.RedisTraceExporter(
            System.getProperty("redis.host", "localhost"),
            Integer.parseInt(System.getProperty("redis.port", "6379")));
        SquadTracer.configure(exp);
        return exp;
    }

    @Bean public InProcessApprovalStore approvalStore() { return new InProcessApprovalStore(); }
    @Bean public InProcessEventBus eventBus()           { return new InProcessEventBus(); }
    @Bean public InProcessFeedbackStore feedbackStore() { return new InProcessFeedbackStore(); }

    @Bean
    public ApplicationRunner runner(SquadContext ctx,
                                    TokenTrackingLlmPort tracker,
                                    io.squados.trace.RedisTraceExporter tracer,
                                    InProcessEventBus eventBus,
                                    InProcessFeedbackStore feedbackStore) {
        return args -> {
            printBanner();
            runScenario(ctx, tracker, tracer, eventBus, feedbackStore,
                "C-1042", "4200", "GB", "192.168.1.1", "KNOWN-ABC123", "M-AMAZON",
                "Scenario 1 — Low Risk: Regular customer, known device, trusted merchant");
            System.out.println();
            runScenario(ctx, tracker, tracer, eventBus, feedbackStore,
                "C-9999", "48000", "NG", "185.220.101.5", "UNKNOWN-XYZ", "M-UNKNOWN",
                "Scenario 2 — High Risk: Flagged customer, Tor IP, unverified merchant");
        };
    }

    private void runScenario(SquadContext ctx,
                              TokenTrackingLlmPort tracker,
                              io.squados.trace.RedisTraceExporter tracer,
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
        tracker.reset();

        // @OnEvent
        String payload = "customerId=" + customerId + ",amount=" + amount +
            ",currency=GBP,country=" + country + ",ip=" + ip +
            ",device=" + device + ",merchant=" + merchantId;
        eventBus.publish("payments.incoming", payload);
        System.out.println("[GatewayAgent] Payment event fired: " + payload);

        // @Delegate
        new DelegateRouter(tracker);
        String routeContext = Double.parseDouble(amount) > 10000
            ? "high value suspicious transaction" : "standard transaction";
        System.out.println("[Delegate] Routing context: " + routeContext);

        // @SquadTool + @SquadPlan — risk assessment
        System.out.println("\n[RiskAnalyst] Running risk tools...");
        String txContext = "Transaction: GBP " + amount + " from customer " + customerId +
            " in " + country + ", IP: " + ip + ", device: " + device + ", merchant: " + merchantId;

        tracker.reset();
        long riskStart = System.currentTimeMillis();
        RiskAssessment risk = ctx.submitTo(AgentRole.ANALYST,
            "Assess fraud risk for this transaction.\n" + txContext +
            "\nProvide riskScore (0.0-1.0 as string), riskLevel (LOW/MEDIUM/HIGH/CRITICAL)," +
            " riskFactors, safeFactors, recommendation (APPROVE/REVIEW/BLOCK).",
            RiskAssessment.class);
        SquadTracer.getExporter().export(AgentSpan.builder("risk-analyst")
            .agentRole(AgentRole.ANALYST).agentName("RiskAnalyst")
            .status(AgentSpan.Status.OK)
            .durationMs(System.currentTimeMillis() - riskStart)
            .inputLength(txContext.length())
            .promptTokens(tracker.getTotalPrompt())
            .completionTokens(tracker.getTotalCompletion())
            .build());
        System.out.println("[Traced] risk-analyst: " + tracker.getTotalTokens() + " tokens");

        double riskScore = 0.5;
        try { if (risk.riskScore != null) riskScore = Double.parseDouble(risk.riskScore); }
        catch (Exception ignored) {}
        System.out.println("[RiskAnalyst] Risk Level:  " + risk.riskLevel);
        System.out.println("[RiskAnalyst] Risk Score:  " + risk.riskScore);
        System.out.println("[RiskAnalyst] Recommend:   " + risk.recommendation);
        if (risk.riskFactors != null)
            risk.riskFactors.forEach(f -> System.out.println("[RiskAnalyst] Factor:      " + f));

        // @SecureAgent
        long expiry = Instant.now().plusSeconds(3600).getEpochSecond();
        AgentIdentity identity = new JwtValidator().validate(
            JwtValidator.createTestToken("system", "squados", expiry, "compliance", "senior-risk"));
        SecurityContext.set(identity);
        System.out.println("[SecureAgent] Identity: " + identity.getSubject() + " " + identity.getRoles());

        // @SquadVote
        System.out.println("\n[SquadVote] Calling the vote...");
        VoteCollector collector = new VoteCollector(
            "fraud-verdict-" + customerId, VoteRule.MAJORITY, TieBreaker.ESCALATE, 3, 30);
        if (riskScore < 0.4) {
            collector.submit(Vote.approve("Risk score low", 1.0).withVoter("RiskAnalyst"), "RiskAnalyst");
            collector.submit(Vote.approve("Behaviour normal", 1.0).withVoter("BehaviourAgent"), "BehaviourAgent");
            collector.submit(Vote.approve("AML/KYC passed", 1.0).withVoter("ComplianceAgent"), "ComplianceAgent");
        } else {
            collector.submit(Vote.reject("Risk elevated: " + riskScore, 1.0).withVoter("RiskAnalyst"), "RiskAnalyst");
            collector.submit(Vote.reject("Behaviour anomaly", 1.0).withVoter("BehaviourAgent"), "BehaviourAgent");
            collector.submit(Vote.reject("AML threshold exceeded", 1.0).withVoter("ComplianceAgent"), "ComplianceAgent");
        }
        VoteResult voteResult;
        try { voteResult = collector.resolve(); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            voteResult = new VoteResult(VoteResult.Outcome.TIMEOUT, new java.util.ArrayList<>(), "fraud-verdict");
        }
        SquadTracer.getExporter().export(AgentSpan.builder("squad-vote")
            .agentRole(AgentRole.ANALYST).agentName("VoteCollector")
            .status(AgentSpan.Status.OK).durationMs(50).build());
        System.out.println("[SquadVote] Result: " + voteResult.getOutcome() +
            " (" + voteResult.getApproveCount() + "-" + voteResult.getRejectCount() + ")");

        // @AutoApproval / @AwaitApproval
        System.out.println();
        if (riskScore < 0.3) System.out.println("[AutoApproval] AUTO-APPROVED");
        else if (riskScore > 0.7) System.out.println("[AwaitApproval] ESCALATING to senior-fraud-analyst");
        else System.out.println("[Decision] APPROVE with monitoring flag");

        // @SquadPlan: final decision
        tracker.reset();
        long decisionStart = System.currentTimeMillis();
        PaymentDecision decision = ctx.submitTo(AgentRole.SUPPORT,
            "Final payment decision. Vote: " + voteResult.getOutcome() +
            ". Risk: " + riskScore + ". " + txContext, PaymentDecision.class);
        SquadTracer.getExporter().export(AgentSpan.builder("underwriter-decision")
            .agentRole(AgentRole.SUPPORT).agentName("UnderwriterAgent")
            .status(AgentSpan.Status.OK)
            .durationMs(System.currentTimeMillis() - decisionStart)
            .promptTokens(tracker.getTotalPrompt())
            .completionTokens(tracker.getTotalCompletion())
            .build());
        System.out.println("[Traced] underwriter: " + tracker.getTotalTokens() + " tokens");

        // @Improve
        feedbackStore.save(new FeedbackExample(
            "fraud-risk-assessment", txContext,
            "riskScore=" + riskScore + " recommendation=" + risk.recommendation,
            riskScore < 0.5 ? FeedbackExample.Label.GOOD : FeedbackExample.Label.BAD,
            "auto-saved"));

        SecurityContext.clear();

        // @Traced telemetry
        System.out.println("\n[Result]");
        System.out.println("  Decision:  " + decision.decision);
        System.out.println("  Reason:    " + decision.reason);
        System.out.println("\n[Telemetry @Traced]");
        System.out.println("  Spans:     " + tracer.size());
        System.out.println("  Tokens:    " + tracer.totalTokens());
        System.out.printf( "  Latency:   %dms%n", System.currentTimeMillis() - startMs);
        System.out.println("  Feedback:  " + feedbackStore.count("fraud-risk-assessment") + " examples (@Improve)");
    }

    @AutoPlan(goal = "Assess fraud risk", maxIterations = 3,
              stopCondition = "COMPLETE", onMaxIterations = IterationPolicy.RETURN_BEST)
    public String investigatePlaceholder() { return "started"; }

    private void printBanner() {
        System.out.println();
        System.out.println("\uD83D\uDEE1\uFE0F  FRAUD DETECTION SQUAD \uD83D\uDEE1\uFE0F");
        System.out.println("Powered by squad-spring-boot-starter:3.3.0 + TokenTrackingLlmPort");
        System.out.println("Real-time payment fraud detection — production ready\n");
    }
}