package io.tutoros.agent;

import io.squados.annotation.*;
import io.tutoros.model.VisualAsset;

/**
 * Visualisation Agent — picks a diagram domain and emits a COMPACT spec
 * for a deterministic renderer.
 *
 * Why no raw SVG: LLMs drift on atom positioning, wire crossings, and
 * geometry constraints. Asking the model to emit a tiny domain spec
 * (SMILES for chemistry, Vega-Lite JSON for plots, etc.) and rendering
 * server-side / client-side with a real library is the path that
 * gpai-class output actually requires.
 *
 * v1 supports two domains end-to-end:
 *   - chem  → SMILES string → SmilesDrawer in the browser
 *   - plot  → Vega-Lite v5 spec → vega-embed in the browser
 *
 * Adding a new domain is a 3-line change: add the type to the router
 * prompt, add a spec prompt method, add a renderer on the frontend.
 *
 * Features:
 *   @StructuredOutput — the agent returns a typed VisualAsset
 *   @Traced           — render selection latency tracked
 *   @Retry            — short retry on transient LLM failure
 */
@Agent(
    role        = AgentRole.VISIONARY,
    name        = "VisualisationAgent",
    description = "Picks a diagram domain (chem, plot, …) and emits a compact " +
                  "renderer-ready spec. Never emits raw SVG — the frontend renders " +
                  "via SmilesDrawer / Vega-Lite / etc. for publication-quality output."
)
@StructuredOutput(schema = VisualAsset.class, retryOnMalformed = true, maxRetries = 2)
@Retry(maxAttempts = 2, backoffMs = 1500)
@Traced(spanName = "visualisation-spec")
public class VisualisationAgent {

    /**
     * Single-shot prompt: emit a renderer-ready spec for the concept under
     * the type the pipeline has already routed to. The pipeline calls
     * {@link #selectRenderType} first and pins the type here so weaker LLMs
     * cannot drift into the wrong domain (and so the prompt does not need
     * to ship example SMILES strings that small models tend to copy
     * verbatim under uncertainty — a known in-context contamination mode).
     *
     * The structured-output retry loop still handles spec-level malformation.
     */
    public String visualPrompt(String concept, String learnerLevel, String requiredType) {
        // Pin to a supported type. If the caller passed something we don't
        // render, fall back to the safer default (plot accepts anything that
        // can be tabulated; chem only accepts molecules).
        String t = (requiredType == null) ? "plot" : requiredType.trim().toLowerCase();
        if (!"chem".equals(t) && !"plot".equals(t)
            && !"geometry".equals(t) && !"freebody".equals(t)
            && !"surface3d".equals(t) && !"function2d".equals(t)
            && !"flow".equals(t) && !"circuit".equals(t)) {
            t = "plot";
        }

        String typeBlock = switch (t) {
            case "chem" -> """
              You MUST set type = "chem".
              specJson = {"smiles":"<canonical SMILES for the concept>"}
              Rules:
                - Emit the correct canonical SMILES for THE CONCEPT, not for any
                  example you have seen. Do NOT default to caffeine, benzene,
                  glucose, or any other unrelated molecule.
                - Keep SMILES under 120 characters.
                - If you genuinely cannot recall the SMILES for this concept,
                  set specJson to {"smiles":""} and explain in the caption.
              """;
            case "geometry" -> """
              You MUST set type = "geometry".
              specJson is a tiny scene graph rendered into a 480×280 viewBox by a
              deterministic SVG renderer. Coordinate system: y grows DOWNWARD
              (SVG default). Origin (0,0) is top-left. Keep all coordinates
              inside [10..470] × [10..270] so labels don't clip.

              Shape:
                {
                  "points":   [{"id":"A","x":120,"y":220,"label":"A"}, ...],
                  "segments": [{"from":"A","to":"B","label":"5cm"}, ...],
                  "polygons": [{"points":["A","B","C"],"fill":"#eef2ff"}],
                  "circles":  [{"cx":240,"cy":140,"r":50,"label":"O"}],
                  "arcs":     [{"cx":120,"cy":220,"r":24,"startDeg":0,"endDeg":60,"label":"60°"}]
                }

              Rules:
                - Use ONLY the fields the diagram needs — omit empty arrays.
                - Reference points by id in segments/polygons (not raw coords).
                - Arc angles are in degrees, 0 = east, increasing counter-clockwise.
                - Labels are short (≤ 6 chars). Render lengths/angles, not prose.
                - Pick coordinates so the shape FITS the concept (e.g. a
                  right triangle should LOOK right-angled).
              """;
            case "freebody" -> """
              You MUST set type = "freebody".
              specJson describes a body, an optional supporting surface, and the
              forces acting on it. Renderer draws the body, surface, and force
              vectors with arrowheads + labels. 480×280 viewBox.

              Shape:
                {
                  "body":    {"shape":"block","x":200,"y":140,"w":80,"h":60,"label":"m"},
                                // shape ∈ "block" | "sphere" | "point"
                                // (x,y) is the body's CENTER
                  "surface": {"type":"ground","y":200}
                                // OR {"type":"incline","angle":30,"y":230}
                                // OR null for free-body in mid-air
                  "forces":  [
                    {"label":"W","magnitude":80,"angle":270,"color":"#dc2626"},
                    {"label":"N","magnitude":80,"angle":90, "color":"#2563eb"},
                    {"label":"F","magnitude":60,"angle":0,  "color":"#16a34a"}
                  ]
                }

              Rules:
                - Force angles use STANDARD MATH convention: 0° = east (+x),
                  90° = north (+y, drawn UPWARD on screen — the renderer
                  flips for SVG), 180° = west, 270° = south (downward).
                - magnitude is in arbitrary units; renderer scales the longest
                  vector to ~80px, others proportionally.
                - color is a CSS hex; pick distinct colors for distinct forces.
                - Labels are short (≤ 4 chars): W, N, T, F, fk, fs, etc.
                - Always include weight (W) when there's gravity.
              """;
            case "function2d" -> """
              You MUST set type = "function2d".
              specJson plots one or more mathematical functions from FORMULA
              STRINGS — the renderer samples them, so DO NOT emit data points.

              Cartesian (y = f(x)):
                {"kind":"cartesian",
                 "curves":[{"expr":"sin(x)/x","label":"sinc","color":"#6366f1"}],
                 "xRange":[-10,10], "xLabel":"x", "yLabel":"f(x)"}
              Parametric ((x(t), y(t))):
                {"kind":"parametric","xExpr":"cos(3*t)","yExpr":"sin(2*t)",
                 "tRange":[0,6.2832]}
              Polar (r(theta)):
                {"kind":"polar","rExpr":"1+cos(theta)","thetaRange":[0,6.2832]}

              Rules:
                - Grammar: + - * / ^ %, parentheses, and functions sin cos tan
                  asin acos atan sinh cosh tanh exp ln log log10 sqrt cbrt abs
                  sign floor ceil round min max pow atan2 mod. Constants: pi, e,
                  tau. Variable is x (cartesian), t (parametric), theta (polar).
                - Prefer this over "plot" for PURE functions; use "plot" for
                  data/bar/scatter comparisons.
                - Give each curve a short label; add a 2nd curve only if it aids
                  the concept.
              """;
            case "surface3d" -> """
              You MUST set type = "surface3d".
              specJson drives an interactive Three.js scene from FORMULA STRINGS.
              Same math grammar as function2d. Pick ONE kind:

              Surface (z = f(x,y)):
                {"kind":"surface","expr":"sin(x)*cos(y)",
                 "xRange":[-3,3], "yRange":[-3,3], "segments":48}
              3D curve ((x(t), y(t), z(t))):
                {"kind":"curve","xExpr":"cos(t)","yExpr":"sin(t)","zExpr":"t/6",
                 "tRange":[0,18.8]}
              Vector field (F = (fx,fy,fz) of x,y,z):
                {"kind":"vectorfield","fx":"-y","fy":"x","fz":"0",
                 "range":[-2,2], "density":5}

              Rules:
                - Use surface3d ONLY when the concept genuinely needs a third
                  dimension (multivariable z=f(x,y), saddle points, helices,
                  E/B fields, gradient/curl). Otherwise prefer function2d/plot.
                - Keep segments ≤ 80 and density ≤ 6 (performance).
                - Variables are x,y (surface), t (curve), x,y,z (field).
              """;
            case "flow" -> """
              You MUST set type = "flow".
              specJson = {"mermaid":"<valid Mermaid source>"} rendered by
              Mermaid.js. Use the diagram type that fits the concept:
                - graph TD / flowchart LR  → processes, algorithms, pathways
                - sequenceDiagram          → message/protocol exchanges
                - stateDiagram-v2          → state machines, phase transitions
                - classDiagram / erDiagram → schemas, taxonomies
                - mindmap                  → concept breakdowns

              Example:
                {"mermaid":"flowchart TD\\n  A[Start] --> B{Condition?}\\n  B -->|yes| C[Do X]\\n  B -->|no| D[Do Y]"}

              Rules:
                - Emit ONLY Mermaid DSL — no click handlers, no inline HTML/JS
                  (the renderer runs in strict mode and will reject them).
                - Keep node labels short; 4–12 nodes is the sweet spot.
                - Escape newlines as \\n inside the JSON string.
              """;
            case "circuit" -> """
              You MUST set type = "circuit".
              specJson lists components IN SERIES ORDER around ONE loop; the
              renderer walks a rectangular loop placing each symbol on the wire.

              Shape:
                {"elements":[
                   {"type":"battery","label":"9V"},
                   {"type":"resistor","label":"R1=100Ω"},
                   {"type":"lamp","label":"L"},
                   {"type":"switch","label":"S"}
                ]}

              Rules:
                - type ∈ battery | cell | resistor | capacitor | inductor |
                  lamp | switch | source | ac | voltmeter | ammeter | ground |
                  wire. Unknown types render as plain wire.
                - List them in the order current flows around the loop.
                - 2–8 elements. Labels are short (R1=100Ω, 9V, C, L).
                - This is a SINGLE series loop — for complex topologies with
                  parallel branches, fall back to type "flow" instead.
              """;
            default -> """
              You MUST set type = "plot".
              specJson = a complete Vega-Lite v5 specification with fields:
                $schema, description, width, height, data, mark, encoding.
              Rules:
                - Width 480, height 280.
                - Always include axis titles tied to THE CONCEPT.
                - Pick `mark` from: line | point | bar | area.
                - For function plots, embed `data.values` — an array of
                  {x, y} samples computed from the actual function in the
                  concept (≥ 40 points across a sensible domain). Do not
                  default to a sine wave unless the concept IS a sine wave.
              """;
        };

        return """
            You are emitting a tiny renderer spec for the concept below.
            NEVER produce raw SVG. NEVER produce inline JavaScript.

            Concept: %s
            Audience: %s-level learner.

            %s

            Output requirements (REQUIRED for every field — do not omit any):
              type      : exactly "%s"
              concept   : 2–4 words drawn from THE CONCEPT above
              title     : ≤ 6 words
              caption   : 1 short sentence (≤ 120 chars) about THE CONCEPT
              specJson  : the spec described above, as a JSON OBJECT serialised
                          to a STRING. Escape inner quotes. No markdown fences.
              altText   : 1 sentence describing what is shown, for screen readers.
            """.formatted(concept, learnerLevel, typeBlock, t);
    }

    /**
     * Backwards-compatible 2-arg overload — keeps callers that don't yet
     * pin a type compiling. Lets the agent fall back to "plot" (the
     * domain that fits the most concepts safely).
     */
    public String visualPrompt(String concept, String learnerLevel) {
        return visualPrompt(concept, learnerLevel, selectRenderType(concept));
    }

    /**
     * Quick pre-router used by ContentAgent's tool call when it wants
     * to hint the type without spending an LLM call. The single-shot
     * prompt above will still re-decide if the LLM disagrees.
     */
    public String selectRenderType(String concept) {
        if (concept == null) return "plot";
        String c = concept.toLowerCase();

        // Chem first — wins on explicit molecule cues. Order matters: a
        // prompt like "structure of caffeine molecule" should route to chem
        // before any plot-ish word can hijack it.
        if (c.contains("molecule") || c.contains("compound") || c.contains("smiles") ||
            c.contains("organic chemistry") || c.contains("functional group") ||
            c.contains("structure of ") || c.contains("skeletal ") ||
            c.contains("benzene") || c.contains("alkane") || c.contains("alkene") ||
            c.contains("alcohol") || c.contains("carboxylic") || c.contains("ester") ||
            c.contains("amine") || c.contains("amide") || c.contains("aromatic")) {
            return "chem";
        }

        // Freebody — physics force diagrams. Checked BEFORE geometry because
        // "free-body diagram of a block on an incline" contains "incline"
        // (geometry-ish) but is firmly a physics diagram.
        if (c.contains("free body") || c.contains("free-body") ||
            c.contains("freebody")  || c.contains("force diagram") ||
            c.contains("forces on")  || c.contains("forces acting") ||
            c.contains("newton's second") || c.contains("normal force") ||
            c.contains("tension") || c.contains("friction force") ||
            c.contains("incline") || c.contains("inclined plane") ||
            c.contains("pulley") || c.contains("block on")) {
            return "freebody";
        }

        // Geometry — synthetic / Euclidean shapes, angles, triangles, circles
        // (the geometric figure, not the chemistry-style ring). Checked AFTER
        // chem so "benzene ring" → chem, but BEFORE plot so "triangle ABC"
        // doesn't get hijacked by a stray "function" keyword.
        if (c.contains("triangle") || c.contains("quadrilateral") ||
            c.contains("polygon")  || c.contains("rhombus") ||
            c.contains("trapezoid") || c.contains("parallelogram") ||
            c.contains("congruent") || c.contains("similar triangles") ||
            c.contains("angle bisector") || c.contains("perpendicular bisector") ||
            c.contains("pythagoras") || c.contains("pythagorean") ||
            c.contains("circle theorem") || c.contains("inscribed angle") ||
            c.contains("tangent to a circle") || c.contains("tangent line to") ||
            c.contains("chord ") || c.contains("radius ") ||
            c.contains("euclidean") || c.contains("geometric proof")) {
            return "geometry";
        }

        // Circuit — series-loop electrical schematics. Strong, distinct cues
        // so generic "current"/"energy" prose doesn't hijack a plot.
        if (c.contains("circuit") || c.contains("ohm's law") || c.contains("ohms law") ||
            c.contains("series resistor") || c.contains("resistors in series") ||
            c.contains("voltage divider") || c.contains("kirchhoff") ||
            c.contains("rc circuit") || c.contains("lc circuit") || c.contains("rl circuit") ||
            c.contains("battery and") || c.contains("emf") || c.contains("schematic")) {
            return "circuit";
        }

        // Flow — node/edge process diagrams: algorithms, state machines,
        // protocols, biological pathways, life cycles, taxonomies.
        if (c.contains("flowchart") || c.contains("flow chart") ||
            c.contains("algorithm") || c.contains("pseudocode") ||
            c.contains("state machine") || c.contains("state diagram") ||
            c.contains("sequence diagram") || c.contains("decision tree") ||
            c.contains("class diagram") || c.contains("er diagram") ||
            c.contains("uml") || c.contains("mind map") || c.contains("mindmap") ||
            c.contains("pathway") || c.contains("life cycle") || c.contains("lifecycle") ||
            c.contains("krebs cycle") || c.contains("water cycle") ||
            c.contains("steps to ") || c.contains("process of ") ||
            c.contains("workflow") || c.contains("pipeline")) {
            return "flow";
        }

        // Surface3D — concepts that genuinely need a third dimension.
        if (c.contains("3d ") || c.contains("3-d ") || c.contains("surface plot") ||
            c.contains("z = f") || c.contains("z=f") || c.contains("saddle") ||
            c.contains("paraboloid") || c.contains("multivariable") ||
            c.contains("two-variable") || c.contains("two variable") ||
            c.contains("vector field") || c.contains("vector-field") ||
            c.contains("gradient field") || c.contains("helix") ||
            c.contains("parametric surface") || c.contains("3d curve")) {
            return "surface3d";
        }

        // Function2D — a SINGLE closed-form curve (cartesian / parametric /
        // polar). Routed here (not "plot") when the cue is unmistakably a
        // formula curve; ambiguous "show me data" stays on Vega plot.
        if (c.contains("parametric") || c.contains("polar curve") ||
            c.contains("polar plot") || c.contains("graph of y") ||
            c.contains("plot of y") || c.contains("graph the function") ||
            c.contains("plot the function") || c.contains("y = f(x)") ||
            c.contains("y=f(x)") || c.contains("lissajous")) {
            return "function2d";
        }

        // Plot — function graphs, data plots, distributions, comparisons,
        // and the full trig / algebra family. These are the prompts the
        // learner is most likely to phrase as "show me / graph of / plot of".
        String[] plotMarkers = {
            "graph", "plot", "chart", "rate", "distribution", "histogram",
            "vs ", " vs.", "function of", "function ",
            "sin", "cos", "tan", "sec ", "csc ", "cot ",
            "sine", "cosine", "tangent", "exponential", "logarith",
            "polynomial", "quadratic", "cubic", "linear equation",
            "slope", "intercept", "derivative", "integral",
            "frequency", "amplitude", "wave"
        };
        for (String m : plotMarkers) {
            if (c.contains(m)) return "plot";
        }
        return "plot"; // safest default — most "show me X" requests are graphs
    }
}
