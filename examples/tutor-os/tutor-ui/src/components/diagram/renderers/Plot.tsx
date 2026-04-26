import { useEffect, useRef, useState } from 'react';
import vegaEmbed, { type VisualizationSpec } from 'vega-embed';

/**
 * Renders a Vega-Lite v5 spec into an SVG plot.
 *
 * Why Vega-Lite (not Plotly, not Recharts):
 *   - Vega-Lite is a JSON specification — exactly the shape an LLM emits
 *     reliably with structured output.
 *   - The renderer is a single npm package (vega-embed) and can output
 *     SVG (zoomable, screen-reader-friendly, theme-able).
 *   - The schema is published, so we can validate at the spec layer.
 *
 * Theme: forces SVG output and disables vega-embed's "actions" menu so
 * the plot fits inside the chat bubble cleanly.
 */
export function Plot({ spec, altText }: { spec: object; altText: string }) {
  const hostRef = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;

    setError(null);
    let cancelled = false;

    vegaEmbed(host, spec as VisualizationSpec, {
      actions: false,
      renderer: 'svg',
      // Use the app's accent + text colors via CSS — vega's `config` lets
      // us thread tokens in. Falls back to vega defaults if missing.
      config: {
        background: 'transparent',
        axis:    { labelColor: 'currentColor', titleColor: 'currentColor' },
        title:   { color: 'currentColor' },
        legend:  { labelColor: 'currentColor', titleColor: 'currentColor' },
      },
    })
      .catch((e: unknown) => {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : String(e));
      });

    return () => {
      cancelled = true;
      // vega-embed mounts SVG inside the host — clear it on unmount/update.
      while (host.firstChild) host.removeChild(host.firstChild);
    };
  }, [spec]);

  if (error) {
    return (
      <div className="diagram__fallback diagram__fallback--inline">
        {altText} <span className="muted">({error})</span>
      </div>
    );
  }

  return <div ref={hostRef} className="diagram__plot" role="img" aria-label={altText} />;
}
