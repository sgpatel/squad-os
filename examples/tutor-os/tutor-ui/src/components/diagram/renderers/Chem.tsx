import { useEffect, useRef, useState } from 'react';
// smiles-drawer ships ESM but no .d.ts — declared in @/types/smiles-drawer.d.ts
// The default export is a namespace exposing { SvgDrawer, parse }.
import SmilesDrawer from 'smiles-drawer';

/**
 * Renders a SMILES string into an inline `<svg>` using SmilesDrawer.
 *
 * Why SmilesDrawer (not RDKit-JS, not OpenChemLib):
 *   - 70 KB gz, pure JS, no WASM bootstrap.
 *   - Reads canonical SMILES which is what the LLM emits most reliably.
 *   - Output is real SVG (zoomable, copy-pasteable, screen-reader-able).
 *
 * The parse → draw flow is async (callback-style) so a malformed SMILES
 * just sets an error string instead of throwing into the React tree.
 */
export interface ChemSpec { smiles?: string }

export function Chem({ spec, altText }: { spec: ChemSpec; altText: string }) {
  const svgRef = useRef<SVGSVGElement>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const svg = svgRef.current;
    const smiles = spec?.smiles?.trim();
    if (!svg || !smiles) {
      setError(smiles ? null : 'No SMILES string in spec');
      return;
    }

    setError(null);
    // Clear any previous render before drawing again on update.
    while (svg.firstChild) svg.removeChild(svg.firstChild);

    try {
      const drawer = new SmilesDrawer.SvgDrawer({
        width:  480,
        height: 280,
        bondThickness: 1,
        atomVisualization: 'default',
        compactDrawing: true,
        terminalCarbons: false,
      });

      SmilesDrawer.parse(
        smiles,
        (tree: unknown) => {
          try {
            // Theme honours light / dark via the document attribute the app
            // already toggles for the rest of the UI.
            const isDark = document.documentElement.dataset.mode === 'dark';
            drawer.draw(tree, svg, isDark ? 'dark' : 'light');
          } catch (e) {
            setError(e instanceof Error ? e.message : String(e));
          }
        },
        (err: unknown) => {
          setError('Invalid SMILES: ' + (err instanceof Error ? err.message : String(err)));
        }
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [spec?.smiles]);

  if (error) {
    return <div className="diagram__fallback diagram__fallback--inline">{altText} <span className="muted">({error})</span></div>;
  }

  return (
    <svg
      ref={svgRef}
      className="diagram__svg diagram__svg--chem"
      viewBox="0 0 480 280"
      role="img"
      aria-label={altText}
    />
  );
}
