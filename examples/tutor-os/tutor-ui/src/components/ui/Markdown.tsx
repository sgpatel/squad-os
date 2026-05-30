import { memo, useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';

/**
 * Normalise the math delimiter dialects that LLMs love to emit into
 * the ones remark-math actually understands.
 *
 * Models trained against ChatGPT-style rendering prefer LaTeX-style
 * fences — {@code \(…\)} for inline math and {@code \[…\]} for block
 * math. Some models also emit bare {@code (…)} / {@code […]} with
 * backslash commands inside, expecting the surface to detect math
 * from the {@code \int} / {@code \sum} / {@code \frac} signal.
 *
 * <p>remark-math only knows {@code $…$} / {@code $$…$$}. We do the
 * smallest, safest rewrite that turns the common dialects into dollar
 * delimiters without false-positives on prose parentheses.
 *
 * <h2>Critical: skip content already inside math regions</h2>
 * The bare-paren rewrite below sees a string like {@code p(y|\theta)}
 * and converts it to {@code $y|\theta$}. That's the right move when
 * the LLM emitted a bare paren as math, but it's catastrophic when
 * the source was {@code $p(y|\theta) = \sum…$} — we'd shred the
 * existing inline math by sprinkling extra dollar signs inside it
 * (`$p$y|\theta$ = \sum…$$$`), and remark-math would parse the
 * fragments as alternating italics + broken math.
 *
 * <p>So we tokenise the source into math vs. non-math segments first
 * and only rewrite the non-math chunks. The math segments survive
 * untouched, exactly as the model emitted them.
 */
function normaliseMath(src: string): string {
  if (!src) return src;

  // Match $$…$$ blocks (greedy, can span lines) OR $…$ inline (no
  // embedded newline, supports \$ as a literal). Block must come
  // first in the alternation so $$x$$ isn't mis-parsed as $$ + x + $$
  // (two empty inline matches).
  const mathRe = /\$\$[\s\S]+?\$\$|\$(?:\\.|[^$\\\n])+?\$/g;

  let out = '';
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = mathRe.exec(src)) !== null) {
    if (m.index > last) out += rewriteNonMath(src.slice(last, m.index));
    out += m[0]; // preserve existing math verbatim
    last = m.index + m[0].length;
  }
  if (last < src.length) out += rewriteNonMath(src.slice(last));
  return out;
}

/** Apply the delimiter-dialect rewrites — caller guarantees no $…$ inside. */
function rewriteNonMath(s: string): string {
  let out = s;

  // Escaped LaTeX delimiters are unambiguous — always math.
  //   \[ block \]   →  $$ block $$
  //   \( inline \)  →  $ inline $
  out = out.replace(/\\\[([\s\S]+?)\\\]/g, (_m, inner: string) => `\n\n$$${inner.trim()}$$\n\n`);
  out = out.replace(/\\\(([\s\S]+?)\\\)/g, (_m, inner: string) => `$${inner.trim()}$`);

  // Bracket-on-its-own-line block math: a line that is exactly
  // "[ … ]" AND contains a LaTeX command (\int, \sum, \frac, \alpha,
  // …) is overwhelmingly display math.
  out = out.replace(
    /^[ \t]*\[[ \t]*([^\n\]]+?\\[a-zA-Z]+[\s\S]*?)\][ \t]*$/gm,
    (_m, inner: string) => `$$${inner.trim()}$$`,
  );

  // Inline parens math: a parenthesised group containing a backslash
  // command is very likely inline math (e.g. "P(x_i | \theta)",
  // "( E[X] = \int … )"). Two rules learned the hard way:
  //
  //   1. PULL IN the leading function identifier. A conditional like
  //      "P(x_i | \theta)" must become "$P(x_i | \theta)$", NOT
  //      "P$x_i | \theta$" — stranding the "P" outside the span both
  //      looks wrong and (because it adds a lone, unbalanced "$")
  //      desynchronises every dollar that follows, shredding the rest
  //      of the line into character soup. We greedily absorb an
  //      optional preceding identifier (P, p, E, f, or a \macro).
  //   2. KEEP the parentheses inside the span. Dropping them loses the
  //      grouping that made the expression read as a function call.
  //
  // Body kept short (< 120 chars, no embedded newline) so we don't eat
  // ordinary prose with parenthetical asides.
  out = out.replace(
    /(\\?[A-Za-z]\w*)?\(\s*([^()\n]{1,120}?\\[a-zA-Z]+[^()\n]*?)\s*\)/g,
    (_m, fn: string | undefined, inner: string) => `$${fn ?? ''}(${inner.trim()})$`,
  );

  return out;
}

interface Props {
  /** Raw markdown source. */
  children: string;
  /** Extra className applied to the wrapper. */
  className?: string;
}

/**
 * Markdown renderer used everywhere prose appears (chat, chapter, notes preview).
 *
 *   - GFM: tables, task lists, autolinks, strikethrough.
 *   - Math: $inline$ and $$block$$ via KaTeX.
 *   - Code: rendered as plain <pre><code>; we deliberately skip syntax
 *           highlighting in the base bundle to keep route chunks small.
 *           To opt-in later, lazy-load shiki and replace the `code` mapping.
 *   - Links: open in a new tab with rel=noopener.
 *
 * The component is memo'd because chat messages re-render on every event;
 * markdown parsing is the most expensive thing per message.
 */
export const Markdown = memo(function Markdown({ children, className }: Props) {
  // Memoise the normalised source — string scans aren't free and chat
  // surfaces re-render on every keystroke.
  const src = useMemo(() => normaliseMath(children), [children]);
  return (
    <div className={['md', className].filter(Boolean).join(' ')}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeKatex]}
        components={{
          a: (props) => <a {...props} target="_blank" rel="noopener noreferrer" />,
          // Distinguish fenced code blocks from inline code.
          // react-markdown v10 doesn't pass `inline`, so detect via parent <pre>.
          code: ({ className: cls, children, ...rest }) => {
            const lang = /language-(\w+)/.exec(cls ?? '')?.[1];
            return (
              <code className={cls} data-lang={lang} {...rest}>
                {children}
              </code>
            );
          },
          pre: ({ children }) => <pre className="md__pre">{children}</pre>
        }}
      >
        {src}
      </ReactMarkdown>
    </div>
  );
});
