package com.example;

import io.squados.agent.AgentResponse;
import io.squados.annotation.SquadApplication;
import io.squados.context.SquadContext;
import io.squados.context.SquadRunner;

/**
 * Hello Squad — the minimal SquadOS application.
 *
 * This is the entire main class. SquadOS does the rest.
 *
 * Run with:
 *   export ANTHROPIC_API_KEY=your-key
 *   java -cp ... com.example.Main
 */
@SquadApplication
public class Main {

    public static void main(String[] args) {

        // Boot the squad — scans for @Agent, loads squad.yml, initialises
        SquadContext ctx = SquadRunner.run(Main.class, args);

        // Submit a task to the lead agent (Oracle)
        AgentResponse response = ctx.submit(
            "Plan a tactical assault on the enemy's north gate. "
            + "Enemy has 3 defenders — two at the gate, one sniper on the right flank."
        );

        // Print the result
        System.out.println("\n═══════════════════════════════");
        System.out.println("  Oracle's Response:");
        System.out.println("═══════════════════════════════");
        System.out.println(response.content());
        System.out.printf("%nTokens used: %d  |  Latency: %dms%n",
            response.totalTokens(), response.latency().toMillis());
    }
}
