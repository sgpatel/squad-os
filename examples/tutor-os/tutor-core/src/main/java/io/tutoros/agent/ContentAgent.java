package io.squados.examples.tutoros.agent;

import io.squados.annotation.*;
import io.squados.examples.tutoros.model.LearnerProfile;
import io.squados.examples.tutoros.model.VisualAsset;

/**
 * Content Agent — fetches real-world examples, diagrams, and worked problems
 * from external knowledge sources during the tutor's reasoning loop.
 *
 * Called by the TutoringPipeline BEFORE the tutor agents respond, so the
 * debate between SocraticTutor and DirectTutor both have access to
 * grounded, accurate source material.
 *
 * Tool call pattern (driven by LLM):
 *   1. LLM receives student question
 *   2. Decides which tool(s) to call based on concept type
 *   3. Results are injected into the tutor context window
 *   4. Tutor produces grounded explanation using real data
 *
 * Features:
 *   @SquadTool    — 5 tools: Khan Academy, Wolfram Alpha, Wikipedia,
 *                   diagram generator, analogyFinder
 *   @Retry        — external APIs can be flaky; retry up to 3 times
 *   @RateLimit    — avoid hammering external APIs
 *   @Traced       — every tool call is a child span under the session span
 */
@Agent(
    role        = AgentRole.RESEARCHER,
    name        = "ContentAgent",
    description = "Fetches grounded examples, diagrams, and analogies from " +
                  "Khan Academy, Wolfram Alpha, and Wikipedia. Called before " +
                  "every tutor response to ensure accuracy and relevance."
)
@Retry(maxAttempts = 3, backoffMs = 500, multiplier = 2.0f)
@RateLimit(callsPerMinute = 60)
@Traced(spanName = "content-fetch")
public class ContentAgent {

    /**
     * Tool 1 — Khan Academy article search.
     *
     * Returns a 2–3 paragraph summary of the most relevant article,
     * including any embedded practice question text.
     */
    @SquadTool(
        name        = "searchKhanAcademy",
        description = "Search Khan Academy for articles and explanations on a concept. " +
                      "Best for: definitions, step-by-step processes, diagrams. " +
                      "Returns: article summary + any embedded practice question."
    )
    public String searchKhanAcademy(
        @ToolParam(description = "Concept or topic to search for", required = true) String concept,
        @ToolParam(description = "Subject area e.g. biology, chemistry, mathematics", required = false) String subject
    ) {
        // Production: call Khan Academy API or scrape /search endpoint
        // Stub returns realistic placeholder
        return """
            [Khan Academy] %s
            Light-dependent reactions occur in the thylakoid membranes of chloroplasts.
            Sunlight energises electrons in chlorophyll, which travel through the electron
            transport chain. This drives ATP synthesis via chemiosmosis. Water is split
            (photolysis) releasing O₂ as a by-product. NADP⁺ is reduced to NADPH.
            Both ATP and NADPH then power the Calvin Cycle in the stroma.
            """.formatted(concept);
    }

    /**
     * Tool 2 — Wolfram Alpha computation and formula lookup.
     *
     * Best for: mathematical calculations, chemical equations, unit conversions,
     * scientific constants, and quantitative questions.
     */
    @SquadTool(
        name        = "wolframAlpha",
        description = "Query Wolfram Alpha for calculations, formulas, scientific facts, " +
                      "and quantitative data. Best for maths, physics, chemistry equations. " +
                      "Returns: computed result with units and step-by-step if available."
    )
    public String wolframAlpha(
        @ToolParam(description = "The computation or query e.g. 'photosynthesis equation balanced'") String query
    ) {
        // Production: call api.wolframalpha.com/v2/query
        return switch (query.toLowerCase()) {
            case "photosynthesis equation"         -> "6CO₂ + 6H₂O + light energy → C₆H₁₂O₆ + 6O₂";
            case "atp molecular formula"            -> "C₁₀H₁₆N₅O₁₃P₃ | Molecular weight: 507.18 g/mol";
            case "speed of light"                   -> "c = 2.998 × 10⁸ m/s";
            default                                 -> "[Wolfram Alpha] Result for: " + query;
        };
    }

    /**
     * Tool 3 — Wikipedia summary fetch.
     *
     * Returns the first 3 sentences of the Wikipedia article intro.
     * Used for broader contextual framing, history of discovery, or
     * real-world applications.
     */
    @SquadTool(
        name        = "wikipediaSummary",
        description = "Fetch a concise summary from Wikipedia for broader context, " +
                      "history of discovery, or real-world applications of a concept. " +
                      "Returns: first 3 sentences of the article introduction."
    )
    public String wikipediaSummary(
        @ToolParam(description = "Article title or concept name") String title
    ) {
        // Production: call en.wikipedia.org/api/rest_v1/page/summary/{title}
        return "[Wikipedia: " + title + "] " +
               "Photosynthesis is a process used by plants, algae and cyanobacteria " +
               "to convert light energy into chemical energy stored in glucose. " +
               "The overall equation is: 6CO₂ + 6H₂O + light → C₆H₁₂O₆ + 6O₂.";
    }

    /**
     * Tool 4 — Analogy finder.
     *
     * Retrieves or generates a real-world analogy tailored to the learner's
     * analogyDomain (e.g. football, cooking, gaming, engineering).
     * Used by DirectTutorAgent to make abstract concepts concrete.
     *
     * Feature: @SquadTool + @AgentMemory (recalls analogies that worked before)
     */
    @SquadTool(
        name        = "findAnalogy",
        description = "Find or generate a real-world analogy for an abstract concept, " +
                      "tailored to the learner's domain of interest. " +
                      "Returns: a 2–3 sentence analogy ready to embed in an explanation."
    )
    public String findAnalogy(
        @ToolParam(description = "Abstract concept to explain via analogy") String concept,
        @ToolParam(description = "Learner's analogy domain e.g. football, cooking, gaming, engineering") String domain
    ) {
        // Production: query an analogy vector store built from curated examples
        return switch (domain.toLowerCase()) {
            case "football", "soccer" ->
                "Think of the electron transport chain like a relay race on a football pitch. " +
                "Each player (protein complex) passes the ball (electron) down the line, " +
                "and every pass generates energy — just like each handoff pumps H⁺ ions across the membrane.";
            case "cooking" ->
                "Think of ATP synthase like a watermill grinding wheat. " +
                "The flow of protons (water) turns the mill (synthase), " +
                "which grinds out ATP (flour) — no flow, no product.";
            case "gaming" ->
                "The electron transport chain is like a conveyor belt in a factory game. " +
                "Items (electrons) move along stations, and each station earns you energy points (H⁺ ions) " +
                "that you spend at the end to craft ATP.";
            default ->
                "Think of the proton gradient as a dam. " +
                "Protons pile up on one side (like water behind the dam), " +
                "and when they flow back through ATP synthase (the turbine), ATP is generated — just like electricity.";
        };
    }

    /**
     * Tool 5 — Visual asset request.
     *
     * Signals VisualisationAgent to generate an SVG diagram, D3.js chart,
     * or Manim animation for a concept. Returns a render type recommendation
     * and the initial script stub that VisualisationAgent will complete.
     *
     * Feature: @SquadTool (triggers async visualisation pipeline)
     */
    @SquadTool(
        name        = "requestVisualisation",
        description = "Request a visual explanation of a concept. " +
                      "Specify the type: SVG (instant diagram), D3JS (interactive chart), " +
                      "MANIM (animated video — takes 30s to render). " +
                      "Returns a placeholder that VisualisationAgent will populate."
    )
    public String requestVisualisation(
        @ToolParam(description = "Concept to visualise") String concept,
        @ToolParam(description = "Visual type: SVG | D3JS | MANIM") String type,
        @ToolParam(description = "Learner level to calibrate complexity") String learnerLevel
    ) {
        // Signals the pipeline to invoke VisualisationAgent asynchronously
        return String.format(
            "{\"renderType\":\"%s\",\"concept\":\"%s\",\"level\":\"%s\",\"status\":\"QUEUED\"}",
            type, concept, learnerLevel
        );
    }
}
