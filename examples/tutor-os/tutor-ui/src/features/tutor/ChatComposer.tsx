import { useEffect, useRef, useState } from 'react';
import { Send, Mic, Paperclip } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Kbd } from '@/components/ui/Misc';

interface Props {
  onSubmit: (body: string) => void;
  disabled?: boolean;
}

/**
 * Auto-growing textarea + send button. Enter sends; Shift+Enter newline.
 */
export function ChatComposer({ onSubmit, disabled }: Props) {
  const ref = useRef<HTMLTextAreaElement>(null);
  const [val, setVal] = useState('');

  // Auto-resize. Capped at ~10 rows by CSS max-height.
  useEffect(() => {
    const ta = ref.current;
    if (!ta) return;
    ta.style.height = 'auto';
    ta.style.height = ta.scrollHeight + 'px';
  }, [val]);

  const send = () => {
    const text = val.trim();
    if (!text || disabled) return;
    onSubmit(text);
    setVal('');
  };

  return (
    <div className="chat__composer">
      <div className="chat__composer-inner">
        <Button variant="ghost" iconOnly type="button" aria-label="Attach"><Paperclip size={14} /></Button>
        <textarea
          ref={ref}
          className="chat__textarea"
          placeholder={disabled ? 'Tutor is composing…' : 'Ask the tutor anything — Enter to send, Shift+Enter for newline'}
          value={val}
          rows={1}
          disabled={disabled}
          onChange={(e) => setVal(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); }
          }}
        />
        <Button variant="ghost" iconOnly type="button" aria-label="Voice"><Mic size={14} /></Button>
        <Button variant="primary" onClick={send} disabled={disabled || !val.trim()} trailing={<Kbd>↩</Kbd>}>
          <Send size={14} />
        </Button>
      </div>
    </div>
  );
}
