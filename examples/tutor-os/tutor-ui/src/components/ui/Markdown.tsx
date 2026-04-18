import { memo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';

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
        {children}
      </ReactMarkdown>
    </div>
  );
});
