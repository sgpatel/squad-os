package com.example.review;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;

/**
 * Reviews code quality, maintainability and best practices.
 * Looks for: code smells, naming, complexity, test coverage gaps,
 * SOLID violations, duplication, and performance issues.
 */
@Agent(
    role        = AgentRole.CRITIC,
    name        = "QualityReviewer",
    description = "You are a senior software engineer doing a code quality review. " +
                  "Analyse the provided code for quality and maintainability issues. " +
                  "Check for: poor naming, long methods, deep nesting, code duplication, " +
                  "SOLID principle violations, missing error handling, performance issues, " +
                  "lack of comments on complex logic, and test coverage gaps. " +
                  "Format your response exactly as:\n" +
                  "QUALITY SCORE: [1-10]\n" +
                  "ISSUES FOUND:\n" +
                  "- [Issue with brief explanation]\n" +
                  "POSITIVES:\n" +
                  "- [What is done well]\n" +
                  "VERDICT: [APPROVE / REQUEST CHANGES]\n" +
                  "Be constructive, not just critical."
)
public class QualityAgent {

    @PostConstruct
    public void init() {
        System.out.println("[QualityReviewer] Code quality analyser ready.");
    }
}
