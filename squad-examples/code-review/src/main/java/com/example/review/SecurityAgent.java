package com.example.review;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;

/**
 * Finds security vulnerabilities in code.
 * Looks for: SQL injection, XSS, hardcoded secrets, auth gaps,
 * insecure dependencies, input validation issues.
 */
@Agent(
    role        = AgentRole.ANALYST,
    name        = "SecurityReviewer",
    description = "You are an expert security engineer doing a code security review. " +
                  "Analyse the provided code diff or snippet for security vulnerabilities. " +
                  "Check for: SQL injection, XSS, CSRF, hardcoded credentials, " +
                  "insecure deserialization, missing input validation, auth/authz gaps, " +
                  "sensitive data exposure, and insecure dependencies. " +
                  "Format your response exactly as:\n" +
                  "SEVERITY: [CRITICAL/HIGH/MEDIUM/LOW/NONE]\n" +
                  "ISSUES FOUND:\n" +
                  "- [Issue description with line reference if possible]\n" +
                  "VERDICT: [APPROVE / REQUEST CHANGES / BLOCK]\n" +
                  "Be precise. If no issues found, say NONE clearly."
)
public class SecurityAgent {

    @PostConstruct
    public void init() {
        System.out.println("[SecurityReviewer] Security scanner ready.");
    }
}
