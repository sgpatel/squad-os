import { lazy, Suspense } from 'react';
import type { VisualAsset } from '@/lib/types';

/**
 * `<Diagram />` — type-routed renderer for VisualAsset.
 *
 * The VisualisationAgent picks a `type` (chem | plot | …) and emits a
 * tiny `specJson` string. This component:
 *   1. picks the renderer for that type (lazy-loaded — heavy libraries
 *      like vega-embed only load when a chat actually contains a plot),
 *   2. parses `specJson` once,
 *   3. hands the parsed object to the renderer.
 *
 * Failure modes degrade gracefully: a parse error or an unsupported
 * type renders the `altText` so the learner still gets context.
 */

// Lazy renderers — keep the route chunk small for learners who never
// trigger a visual. Each chunk is <60 KB gz on its own; loaded on demand.
const ChemRenderer = lazy(() => import('./renderers/Chem').then(m => ({ default: m.Chem })));
const PlotRenderer = lazy(() => import('./renderers/Plot').then(m => ({ default: m.Plot })));

export function Diagram({ asset }: { asset: VisualAsset }) {
  const { spec, parseError } = parseSpec(asset.specJson);

  const renderer = pickRenderer(asset.type);

  return (
    <figure className="diagram" aria-label={asset.altText}>
      {asset.title && <figcaption className="diagram__title">{asset.title}</figcaption>}

      <div className="diagram__canvas" role="img" aria-label={asset.altText}>
        {parseError ? (
          <DiagramFallback
            altText={asset.altText}
            reason={`Spec is not valid JSON: ${parseError}`}
          />
        ) : !renderer ? (
          <DiagramFallback
            altText={asset.altText}
            reason={`Diagram type "${asset.type}" is not supported yet`}
          />
        ) : (
          <Suspense fallback={<DiagramSkeleton />}>
            {renderer === 'chem' ? (
              <ChemRenderer spec={spec as { smiles?: string }} altText={asset.altText} />
            ) : (
              <PlotRenderer spec={spec as object} altText={asset.altText} />
            )}
          </Suspense>
        )}
      </div>

      {asset.caption && <p className="diagram__caption">{asset.caption}</p>}
    </figure>
  );
}

/**
 * Parse `specJson` defensively. The Java-side `StructuredOutputParser`
 * stores nested JSON in @OutputField string fields **without** decoding
 * its escape sequences, so a clean LLM emission like
 *   "specJson": "{\"smiles\":\"c1ccccc1\"}"
 * arrives over the wire as the literal characters
 *   {\"smiles\":\"c1ccccc1\"}
 * which is not valid JSON ({@code \"} is illegal at object-key position).
 *
 * Strategy:
 *   1. Try parsing as-is — covers the happy path where the parser was
 *      patched or the LLM happened to emit the spec as a real JSON object.
 *   2. Fallback: unescape one level of JSON escapes (\" → "  \\ → \  \n / \t / \r)
 *      and retry. This recovers the {@code StructuredOutputParser} bug
 *      without needing the same backend fix shipped here.
 *   3. Surface a single human-readable error so the fallback message in
 *      the bubble stays meaningful.
 *
 * Same trick we ship in {@code parseQuestionsJson} for quiz output.
 */
function parseSpec(raw: string | undefined | null): { spec: unknown; parseError: string | null } {
  if (raw == null || raw === '') {
    return { spec: null, parseError: 'empty spec' };
  }
  // Strip optional ```json fences a model might add despite instructions.
  let s = raw.trim();
  if (s.startsWith('```')) {
    s = s.replace(/^```(?:json)?\s*/i, '').replace(/```$/, '').trim();
  }

  // Attempt 1 — clean JSON.
  try { return { spec: JSON.parse(s), parseError: null }; } catch { /* fall through */ }

  // Attempt 2 — unescape one level (the parser-bug case).
  const unescaped = s
    .replace(/\\"/g, '"')
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, '\t')
    .replace(/\\r/g, '\r')
    .replace(/\\\\/g, '\\');
  try { return { spec: JSON.parse(unescaped), parseError: null }; } catch (e) {
    return {
      spec: null,
      parseError: e instanceof Error ? e.message : String(e),
    };
  }
}

function pickRenderer(type: VisualAsset['type']): 'chem' | 'plot' | null {
  if (type === 'chem') return 'chem';
  if (type === 'plot') return 'plot';
  return null; // geometry / freebody / flow / circuit — wired in v2
}

function DiagramSkeleton() {
  return (
    <div className="diagram__skeleton" aria-hidden>
      <span className="diagram__skeleton-bar" />
      <span className="diagram__skeleton-bar" />
      <span className="diagram__skeleton-bar diagram__skeleton-bar--short" />
    </div>
  );
}

function DiagramFallback({ altText, reason }: { altText: string; reason: string }) {
  return (
    <div className="diagram__fallback" role="status">
      <p className="diagram__fallback-alt">{altText}</p>
      <p className="diagram__fallback-reason">Could not render: {reason}</p>
    </div>
  );
}
