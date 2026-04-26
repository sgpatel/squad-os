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
 * Supported types (v1 vertical slice):
 *   "chem"      — `specJson` is a single SMILES string in JSON, e.g.
 *                 {"smiles":"c1ccccc1"}        → renders benzene
 *   "plot"      — `specJson` is a Vega-Lite v5 spec
 *
 * Reserved for follow-ups:
 *   "geometry"  — points/segments/circles → Mafs / JSXGraph
 *   "freebody"  — surfaces/masses/forces[] → custom SVG renderer
 *   "flow"      — Mermaid source → mermaid.js
 *   "circuit"   — netlist → schemdraw-class renderer
 *
 * The structured-output parser in squad-core stores nested JSON as a raw
 * string, so `specJson` is intentionally typed as String — the FE parses
 * it once with `JSON.parse` and hands the object to its renderer.
 */
public class VisualAsset {

    @OutputField(
        description = "Diagram domain. One of: chem | plot | geometry | freebody | flow | circuit. " +
                      "v1 only renders chem and plot — emit one of these unless the concept truly needs another.",
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
                      "For type='chem': {\"smiles\":\"<SMILES>\"}. " +
                      "For type='plot': a complete Vega-Lite v5 spec. " +
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
