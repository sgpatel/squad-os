package io.tutoros.model;

import io.squados.annotation.OutputField;

/**
 * A diagram asset produced by the VisualisationAgent.
 *
 * Architecture (gpai-style domain-spec → deterministic renderer):
 *   1. The LLM picks one of a small set of supported diagram domains.
 *   2. The LLM emits a COMPACT, STRUCTURED spec for that domain — never
 *      raw SVG. The spec is small enough that LLMs are reliable at it.
 *   3. The frontend `&lt;Diagram /&gt;` component routes by `type` and feeds
 *      `specJson` to a deterministic renderer (SmilesDrawer, Vega-Lite,
 *      Mafs, etc.). This produces publication-quality output without
 *      asking the LLM to do any positioning or layout.
 *
 * Supported types (all render end-to-end):
 *   "chem"       — SMILES string → SmilesDrawer, e.g. {"smiles":"c1ccccc1"}
 *   "plot"       — Vega-Lite v5 spec → vega-embed (data/bar/scatter)
 *   "geometry"   — points/segments/circles → custom SVG renderer
 *   "freebody"   — surfaces/masses/forces[] → custom SVG renderer
 *   "function2d" — formula curve (y=f(x) / parametric / polar) → SVG
 *   "surface3d"  — z=f(x,y) / 3D curve / vector field → Three.js (WebGL)
 *   "flow"       — Mermaid source → mermaid.js (flow/state/sequence/…)
 *   "circuit"    — series-loop component list → SVG schematic renderer
 *
 * The structured-output parser in squad-core stores nested JSON as a raw
 * string, so `specJson` is intentionally typed as String — the FE parses
 * it once with `JSON.parse` and hands the object to its renderer.
 */
public class VisualAsset {

    @OutputField(
        description = "Diagram domain. One of: chem | plot | function2d | surface3d | " +
                      "geometry | freebody | flow | circuit. Pick the domain that fits " +
                      "the concept; the pipeline pins the type in the prompt.",
        example     = "chem"
    )
    public String type;

    @OutputField(
        description = "Concept this visual explains in 2–4 words.",
        example     = "benzene structure"
    )
    public String concept;

    @OutputField(
        description = "Short title shown above the diagram.",
        example     = "Benzene (C6H6)"
    )
    public String title;

    @OutputField(
        description = "One-sentence learner-facing caption rendered under the diagram.",
        example     = "A six-carbon aromatic ring with delocalised π-electrons."
    )
    public String caption;

    @OutputField(
        description = "JSON spec for the chosen renderer. " +
                      "chem: {\"smiles\":\"<SMILES>\"}. plot: a Vega-Lite v5 spec. " +
                      "function2d: {\"kind\":\"cartesian\",\"curves\":[{\"expr\":\"sin(x)\"}],\"xRange\":[-6,6]}. " +
                      "surface3d: {\"kind\":\"surface\",\"expr\":\"sin(x)*cos(y)\",\"xRange\":[-3,3],\"yRange\":[-3,3]}. " +
                      "flow: {\"mermaid\":\"flowchart TD\\n A-->B\"}. " +
                      "circuit: {\"elements\":[{\"type\":\"battery\",\"label\":\"9V\"},{\"type\":\"resistor\",\"label\":\"R\"}]}. " +
                      "Always a JSON object; escape inner quotes — never a markdown fence.",
        example     = "{\"smiles\":\"c1ccccc1\"}"
    )
    public String specJson;

    @OutputField(
        description = "Plain-text description of the visual for screen readers and fallback display.",
        example     = "A hexagonal ring of six carbon atoms with alternating double bonds."
    )
    public String altText;
}
