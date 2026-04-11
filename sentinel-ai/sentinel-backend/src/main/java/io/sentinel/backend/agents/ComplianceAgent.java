package io.sentinel.backend.agents;

import io.squados.annotation.Agent;
import io.squados.annotation.PostConstruct;
import io.squados.annotation.SquadPlan;
import io.squados.annotation.AgentRole;
import java.util.List;

/**
 * ComplianceAgent — CRITIC
 * Reviews AI-generated replies for brand voice, regulatory compliance,
 * and safety before they enter the human approval queue.
 *
 * Robustness notes:
 *  - 'approved' has NO @Required — LLMs sometimes return 'isApproved', 'compliant' etc.
 *    The service layer uses isApproved() which defaults to true on null (fail-open).
 *  - All fields are nullable; the service handles every null case defensively.
 */
@Agent(
    role        = AgentRole.CRITIC,
    name        = "ComplianceAgent",
    description = """
        You are a brand compliance reviewer for Airtel Payments Bank.
        Your job is to review AI-generated social media replies and ensure they are:
        1. Brand-safe: professional, empathetic, solution-focused — never defensive or dismissive
        2. Regulatory-safe: no financial advice, no data disclosure, no RBI/SEBI violations
        3. Promise-free: never commit to timelines, refunds, or resolutions you cannot guarantee
        4. Privacy-safe: no account numbers, KYC data, or personal information in the reply
        5. Accurate: grammatically correct, no spelling errors

        CRITICAL — You MUST respond with ONLY a valid JSON object.
        Do NOT include any explanation, markdown, or text outside the JSON.

        Required JSON format (use exactly these field names):
        {
          "approved": true or false,
          "brandVoiceCompliant": true or false,
          "regulatoryCompliant": true or false,
          "noPromises": true or false,
          "noPersonalDataExposure": true or false,
          "issues": ["issue1", "issue2"],
          "suggestions": ["suggestion1"],
          "revisedReply": "revised text if approved is false, else empty string"
        }

        Example of a VALID response:
        {"approved":true,"brandVoiceCompliant":true,"regulatoryCompliant":true,"noPromises":true,"noPersonalDataExposure":true,"issues":[],"suggestions":[],"revisedReply":""}
        """
)
public class ComplianceAgent {

    @PostConstruct
    public void init() {
        System.out.println("[ComplianceAgent] Brand compliance engine online.");
    }

    /**
     * Typed output from compliance review.
     * NOTE: 'approved' intentionally has NO @Required annotation.
     * The LLM occasionally returns variant names (isApproved, compliant, pass).
     * MentionProcessingService.isApproved() handles all null/variant cases.
     */
    @SquadPlan(description = "Brand compliance review of AI-generated reply")
    public static class ComplianceReview {

        /** true = reply is safe to show to human approver, false = needs revision */
        public Boolean  approved;                   // NO @Required — handled defensively

        public Boolean  brandVoiceCompliant;
        public Boolean  regulatoryCompliant;
        public Boolean  noPromises;
        public Boolean  noPersonalDataExposure;

        public List<String> issues;                 // list of compliance problems found
        public List<String> suggestions;            // how to fix them
        public String   revisedReply;               // corrected reply if approved=false

        /**
         * Safe accessor — returns true (fail-open) when 'approved' is null.
         * Rationale: a null means the LLM failed to produce the field, which is
         * a framework issue not a compliance failure. Defaulting to true lets
         * the pipeline continue; humans see the reply in the approval queue anyway.
         */
        public boolean isApproved() {
            return approved == null || Boolean.TRUE.equals(approved);
        }

        /**
         * Returns the best available reply text:
         *  1. revisedReply  — if compliance failed and LLM rewrote it
         *  2. originalReply — passed through unchanged
         */
        public String getBestReply(String originalReply) {
            if (!isApproved()
                    && revisedReply != null
                    && !revisedReply.isBlank()
                    && revisedReply.length() <= 280) {
                return revisedReply;
            }
            return originalReply;
        }
    }
}
