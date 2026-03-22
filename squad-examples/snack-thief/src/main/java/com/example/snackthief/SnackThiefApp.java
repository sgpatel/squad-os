package com.example.snackthief;

import com.example.snackthief.adapters.SpringAiLlmAdapter;
import com.example.snackthief.plans.*;
import io.squados.annotation.*;
import io.squados.approval.*;
import io.squados.config.SquadConfigBridge;
import io.squados.context.SquadContext;
import io.squados.context.SquadRunner;
import io.squados.eval.*;
import io.squados.event.*;
import io.squados.llm.LlmPort;
import io.squados.plan.*;
import io.squados.trace.*;
import io.squados.vote.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@SquadApplication
public class SnackThiefApp {

    public static void main(String[] args) {
        SquadConfigBridge.applyToSystemProperties();
        SpringApplication.run(SnackThiefApp.class, args);
    }

    @Bean public LlmPort llmPort(ChatClient.Builder builder) {
        return new SpringAiLlmAdapter(builder);
    }

    @Bean public SquadContext squadContext(LlmPort llmPort) {
        return SquadRunner.run(SnackThiefApp.class, llmPort);
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

    @Bean
    public ApplicationRunner runner(SquadContext ctx,
                                    InMemoryTraceExporter tracer) {
        return args -> {
            System.out.println("\n\uD83C\uDF55 THE GREAT OFFICE SNACK THIEF INVESTIGATION SQUAD \uD83C\uDF55");
            System.out.println("Powered by SquadOS v2.8 - 17 features, 289 tests\n");

            System.out.println("\uD83D\uDEA8 SNACK ALERT: Dave is PIZZA-LESS and DEVASTATED");
            System.out.println("-".repeat(55));

            String crime = "Victim: Dave from Engineering. Item: 3 slices pepperoni pizza," +
                " labeled DO NOT EAT. Time: 12:00-12:15pm. Fridge smelled of microwave pizza at 12:10pm.";

            System.out.println("\n[SherlockBot] The game is afoot. Filing crime report...");
            CrimeReport report = ctx.submitTo(AgentRole.STRATEGIST,
                "File an official crime report for this snack theft. Be dramatic:\n" + crime,
                CrimeReport.class);

            System.out.println("\nCRIME REPORT: " + report.crimeType +
                " | Victim: " + report.victim + " | Severity: " + report.severity);

            System.out.println("\n[CSI-GPT] Analyzing evidence... *puts on sunglasses*");
            SuspectProfile suspect = ctx.submitTo(AgentRole.ANALYST,
                "Profile suspect Karen from Accounting. Badge scan shows her at fridge 12:02pm." +
                " CCTV shows suspicious pizza enthusiasm. HR has prior food incidents on file." +
                " Provide full suspect profile with guilt score 0.0-1.0.",
                SuspectProfile.class);

            System.out.println("Suspect: " + suspect.name + " | Guilt: " + suspect.guiltScore);
            System.out.println("Motive:  " + suspect.motive);
            System.out.println("Alibi:   " + suspect.alibi);

            System.out.println("\n\u2696\uFE0F  THE VOTE - Is " + suspect.name + " guilty?");
            System.out.println("-".repeat(55));

            VoteCollector collector = new VoteCollector(
                "Snack Verdict", VoteRule.MAJORITY, TieBreaker.ESCALATE, 5, 30);

            double guilt = 0.85;
            try { if (suspect.guiltScore != null) guilt = Double.parseDouble(suspect.guiltScore); } catch (Exception ignored) {}
            collector.submit(Vote.approve("Badge scan + cheese breath = GUILTY", guilt).withVoter("SherlockBot"), "SherlockBot");
            collector.submit(Vote.approve("Evidence never lies. Case closed \uD83D\uDE0E", guilt).withVoter("CSI-GPT"), "CSI-GPT");
            collector.submit(Vote.approve("I KNEW IT. I have RECEIPTS!!", guilt).withVoter("GossipAgent"), "GossipAgent");
            collector.submit(Vote.reject("We need form HR-2024-SNACK first.", 0.5).withVoter("HRBot"), "HRBot");
            collector.submit(Vote.approve("DISMISSED.", guilt).withVoter("JudgeJudy"), "JudgeJudy");

            VoteResult voteResult = collector.resolve();
            System.out.println("RESULT: " + voteResult.getOutcome() +
                " (" + voteResult.getApproveCount() + "-" + voteResult.getRejectCount() + ")");

            if (guilt > 0.7) {
                System.out.println("\n\u2705 @AutoApproval: Guilt " + guilt + " > 0.7 - AUTO-PROSECUTE");
            }

            System.out.println("\n[JudgeJudy] Court is in session. This will not take long.");
            Verdict verdict = ctx.submitTo(AgentRole.CRITIC,
                "Deliver verdict. Suspect: " + suspect.name +
                ". Vote: " + voteResult.getApproveCount() + "-" + voteResult.getRejectCount() +
                ". Multiple prior HR food incidents. Be JudgeJudy. No appeals.",
                Verdict.class);

            System.out.println("\nVERDICT:  " + verdict.verdict);
            System.out.println("CULPRIT:  " + verdict.culprit);
            System.out.println("SENTENCE: " + verdict.sentence);
            if (verdict.dramaticClosing != null)
                System.out.println("\n\uD83D\uDD28 " + verdict.dramaticClosing);

            System.out.println("\n\uD83D\uDCCA @Traced telemetry:");
            System.out.println("  Spans: " + tracer.size() + " | Tokens: " + tracer.totalTokens());
            System.out.printf("  Avg latency: %.0fms%n", tracer.avgDurationMs());

            System.out.println("\n" + "=".repeat(55));
            System.out.println("  Justice served. Dave gets replacement pizza.");
            System.out.println("=".repeat(55));
        };
    }

    @AutoPlan(goal="Find the snack thief", maxIterations=3,
              stopCondition="SOLVED", onMaxIterations=IterationPolicy.RETURN_BEST)
    public String investigatePlaceholder() { return "started"; }
}