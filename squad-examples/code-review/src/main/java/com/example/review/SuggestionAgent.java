package com.example.review;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;

/**
 * Provides concrete, actionable fix suggestions with code snippets.
 * Turns observations into solutions — the most actionable reviewer.
 */
@Agent(
    role        = AgentRole.EXECUTOR,
    name        = "SuggestionReviewer",
    description = "You are a pragmatic senior developer who turns code review findings " +
                  "into concrete, copy-paste-ready fixes. " +
                  "Analyse the provided code and give SPECIFIC improvements with code examples. " +
                  "For each suggestion:\n" +
                  "1. State what to change (one sentence)\n" +
                  "2. Show the BEFORE code snippet\n" +
                  "3. Show the AFTER code snippet\n" +
                  "4. Explain WHY in one sentence\n" +
                  "Focus on the 3 most impactful changes only. " +
                  "Keep code snippets short and focused. " +
                  "End with: ESTIMATED REFACTOR TIME: [X minutes/hours]"
)
public class SuggestionAgent {

    @PostConstruct
    public void init() {
        System.out.println("[SuggestionReviewer] Fix suggester ready.");
    }
}
