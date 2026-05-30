import { useEffect, useRef, useState } from 'react';
import mermaid from 'mermaid';

/**
 * Mermaid renderer for node-and-edge STEM diagrams: flowcharts &
 * algorithms, state machines, sequence diagrams, class/ER schemas,
 * biology pathways, decision trees, mind maps.
 *
 * Mermaid is a text DSL — exactly the shape an LLM emits reliably — and
 * lays out the graph deterministically, so we never ask the model to
 * position nodes. We feed it the raw source from the spec and mount the
 * returned SVG.
 *
 * Theme follows the app's light/dark mode. `mermaid.render` is async and
 * throws on a syntax error, which we surface as the diagram fallback
 * rather than letting it escape into the React tree.
 */

export interface MermaidSpec { mermaid?: string; source?: string; code?: string }

let initialised = false;
function ensureInit(isDark: boolean) {
  // mermaid keeps global config; re-init on theme flips so a dark-mode
  // toggle re-colours subsequent renders.
  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'strict', // no click-handlers / inline scripts from LLM source
    theme: isDark ? 'dark' : 'default',
    fontFamily: 'inherit',
  });
  initialised = true;
}

let seq = 0;

export function Mermaid({ spec, altText }: { spec: MermaidSpec | string; altText: string }) {
  const hostRef = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);

  const source = (typeof spec === 'string' ? spec : spec?.mermaid ?? spec?.source ?? spec?.code ?? '').trim();

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    if (!source) { setError('No mermaid source in spec'); return; }

    setError(null);
    let cancelled = false;
    const isDark = document.documentElement.dataset.mode === 'dark';
    if (!initialised) ensureInit(isDark);
    else ensureInit(isDark); // cheap; keeps theme in sync

    const id = `mmd-${Date.now()}-${seq++}`;
    mermaid
      .render(id, source)
      .then(({ svg }) => {
        if (cancelled) return;
        host.innerHTML = svg;
        // make the SVG fluid inside the bubble
        const el = host.querySelector('svg');
        if (el) { el.removeAttribute('height'); el.style.maxWidth = '100%'; el.style.height = 'auto'; }
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        // mermaid leaves an orphan error node in <body> on failure — clean it
        document.getElementById(id)?.remove();
        document.getElementById(`d${id}`)?.remove();
        setError(e instanceof Error ? (e.message.split('\n')[0] ?? e.message) : String(e));
      });

    return () => { cancelled = true; };
  }, [source]);

  if (error) {
    return <div className="diagram__fallback diagram__fallback--inline">{altText} <span className="muted">({error})</span></div>;
  }
  return <div ref={hostRef} className="diagram__mermaid" role="img" aria-label={altText} />;
}
