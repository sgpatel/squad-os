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
 */
function normaliseMath(src: string): string {
  if (!src) return src;
  let out = src;

  // Escaped LaTeX delimiters are unambiguous — always math.
  //   \[ block \]   →  $$ block $$
  //   \( inline \)  →  $ inline $
  out = out.replace(/\\\[([\s\S]+?)\\\]/g, (_m, inner) => `\n\n$$${inner.trim()}$$\n\n`);
  out = out.replace(/\\\(([\s\S]+?)\\\)/g, (_m, inner) => `$${inner.trim()}$`);

  // Bracket-on-its-own-line block math: a line that is exactly
  // "[ … ]" AND contains a LaTeX command (\int, \sum, \frac, \alpha,
  // …) is overwhelmingly display math.
  out = out.replace(
    /^[ \t]*\[[ \t]*([^\n\]]+?\\[a-zA-Z]+[\s\S]*?)\][ \t]*$/gm,
    (_m, inner: string) => `$$${inner.trim()}$$`,
  );

  // Inline parens math: "( … )" containing a backslash-command is
  // very likely inline math (e.g. "( E[X] )", "( \int … )"). Keep the
  // body short (< 120 chars, no embedded newline) to avoid eating
  // ordinary sentences with parenthetical asides.
  out = out.replace(
    /\(\s*([^()\n]{1,120}?\\[a-zA-Z]+[^()\n]*?)\s*\)/g,
    (_m, inner: string) => `$${inner.trim()}$`,
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
