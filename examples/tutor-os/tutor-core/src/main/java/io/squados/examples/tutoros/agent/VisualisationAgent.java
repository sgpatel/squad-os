package io.squados.examples.tutoros.agent;

import io.squados.annotation.*;
import io.squados.examples.tutoros.model.VisualAsset;
import io.squados.remote.SquadClient;

/**
 * Visualisation Agent — generates SVG diagrams, D3.js charts, and
 * Manim animations to explain concepts visually.
 *
 * Selection logic (picked by the LLM based on concept type):
 *   SVG   → static diagrams, labelled structures, flow charts
 *           (instant render, embedded directly in chat as HTML)
 *   D3JS  → interactive graphs, data plots, animations that the
 *           student can manipulate in-browser (no server needed)
 *   MANIM → cinematic step-by-step mathematical/scientific animations
 *           (Python subprocess on the server; takes 20–40s to render)
 *
 * Triggered by ContentAgent's requestVisualisation() tool call.
 * Runs asynchronously — the chat message shows a placeholder while
 * the render completes, then updates via WebSocket push.
 *
 * Future scope note (from product design):
 *   - SVG animations via CSS/SMIL for step-by-step process diagrams
 *   - D3.js simulation (e.g. particle model of gas pressure)
 *   - Manim integration via REST API call to a Python service
 *   - Learner-interactive: student can drag labels onto blank diagrams
 *
 * Features:
 *   @StructuredOutput — typed VisualAsset with render payload
 *   @RemoteSquad      — calls Python Manim render service for MANIM type
 *   @Traced           — render latency tracked per asset type
 *   @Retry            — Manim server can be slow; retry up to 2×
 */
@Agent(
    role        = AgentRole.EXECUTOR,
    name        = "VisualisationAgent",
    description = "Generates SVG diagrams, D3.js interactive charts, and Manim " +
                  "animations for any science/maths concept. Embedded directly " +
                  "in the chat or linked as a rendered video."
)
@StructuredOutput(schema = VisualAsset.class, retryOnMalformed = true, maxRetries = 2)
@Retry(maxAttempts = 2, backoffMs = 2000)
@Traced(spanName = "visualisation-render")
public class VisualisationAgent {

    /**
     * Python Manim render service — called for MANIM type assets.
     * Returns a video URL after async render completes.
     *
     * Feature: @RemoteSquad
     */
    @RemoteSquad(url = "${tutor.manim.url}", auth = "api-key", timeoutMs = 60000)
    private SquadClient manimService;

    /**
     * Prompt for SVG diagram generation.
     *
     * The LLM produces inline SVG markup that is embedded directly
     * in the chat bubble as an HTML element.
     */
    public String svgPrompt(String concept, String learnerLevel) {
        return """
            Generate a clean, labelled SVG diagram explaining: %s
            Audience: %s-level learner.

            Requirements:
            - Use SVG 1.1, viewBox="0 0 600 400", no external dependencies
            - Colour scheme: dark background (#1a1a2e), bright labels (#e6edf3)
            - Use arrows (marker-end) to show direction/flow
            - Label every component clearly with <text> elements
            - Include a <title> and a one-sentence <desc> for accessibility
            - Add simple CSS animation for key moving parts (electron flow, etc.)
              using @keyframes inside a <style> block

            Output ONLY the SVG markup — no prose, no markdown fences.
            """.formatted(concept, learnerLevel);
    }

    /**
     * Prompt for D3.js interactive visualisation generation.
     *
     * The LLM produces a self-contained JavaScript snippet that
     * renders into a <div id="viz-[id]"> injected into the chat.
     */
    public String d3Prompt(String concept, String dataContext, String learnerLevel) {
        return """
            Generate a self-contained D3.js v7 visualisation for: %s
            Audience: %s-level learner.

            Data context: %s

            Requirements:
            - Self-contained: all D3 code in one <script> block
            - Renders into: document.getElementById('viz-TARGET')
            - Width: 560px, height: 320px, responsive
            - Dark theme: background #161b22, text #e6edf3
            - Interactive: tooltip on hover showing exact values
            - Animated: transitions on load (300ms ease)
            - Include a legend if multiple data series

            Output ONLY the JavaScript code — no HTML wrapper, no markdown.
            """.formatted(concept, learnerLevel, dataContext);
    }

    /**
     * Prompt for Manim scene script generation.
     *
     * The LLM writes a Python Manim scene. The manimService compiles
     * and renders it server-side, returning a video URL.
     *
     * Future scope: stream render progress via WebSocket.
     */
    public String manimPrompt(String concept, String learnerLevel) {
        return """
            Write a Python Manim (Community Edition) scene script explaining: %s
            Audience: %s-level learner — calibrate mathematical complexity accordingly.

            Requirements:
            - Scene class name: TutorOSScene (extends Scene)
            - Duration: 60–90 seconds of animation
            - Use MathTex for equations, Text for labels
            - Animate step-by-step: show each component before the next appears
            - Use color_theme: DARK (dark background, bright objects)
            - Include a narrator text at bottom for each animation step
            - End with a summary slide showing the key equation/diagram

            Output ONLY the Python code — no prose.
            """.formatted(concept, learnerLevel);
    }

    /**
     * Selects the best render type for a given concept and learner context.
     *
     * Called by ContentAgent's requestVisualisation() tool before
     * invoking this agent — allows pre-routing without an LLM call.
     */
    public String selectRenderType(String concept, String learnerLevel, boolean fastMode) {
        if (fastMode) return "SVG"; // always fast in exam revision mode
        String c = concept.toLowerCase();
        if (c.contains("graph") || c.contains("rate") || c.contains("plot") ||
            c.contains("spectrum") || c.contains("distribution")) return "D3JS";
        if (c.contains("animation") || c.contains("step by step") ||
            c.contains("mechanism") || c.contains("derive")) return "MANIM";
        return "SVG"; // default for diagrams, structures, flow charts
    }
}
