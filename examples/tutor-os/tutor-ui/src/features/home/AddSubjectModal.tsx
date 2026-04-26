import { useEffect, useRef, useState } from 'react';
import { X, Atom, Book, Cloud, Code, FlaskConical, Leaf, Sigma } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Kbd } from '@/components/ui/Misc';

/**
 * AddSubjectModal — lightweight dialog to add a user-defined subject.
 *
 * Kept deliberately small: name, one-line description, colour + icon. The
 * first message sent to the tutor for this subject generates the plan /
 * chapters on the backend, so no course-authoring is required up front.
 */

interface Props {
  open: boolean;
  onClose: () => void;
  onAdd: (input: { name: string; blurb: string; color: string; icon: string }) => void;
}

const COLORS = [
  '#6366f1', // indigo (default)
  '#16a34a', // green
  '#0ea5e9', // sky
  '#f59e0b', // amber
  '#8b5cf6', // violet
  '#dc2626', // red
  '#2563eb', // blue
  '#ec4899'  // pink
];

const ICONS: Array<{ name: string; Icon: LucideIcon }> = [
  { name: 'Book',         Icon: Book },
  { name: 'Atom',         Icon: Atom },
  { name: 'Leaf',         Icon: Leaf },
  { name: 'FlaskConical', Icon: FlaskConical },
  { name: 'Sigma',        Icon: Sigma },
  { name: 'Code',         Icon: Code },
  { name: 'Cloud',        Icon: Cloud }
];

export function AddSubjectModal({ open, onClose, onAdd }: Props) {
  const [name,  setName]  = useState('');
  const [blurb, setBlurb] = useState('');
  const [color, setColor] = useState(COLORS[0]!);
  const [icon,  setIcon]  = useState(ICONS[0]!.name);

  const firstInputRef = useRef<HTMLInputElement>(null);

  // Reset + focus on open, so the dialog feels fresh each time.
  useEffect(() => {
    if (open) {
      setName(''); setBlurb('');
      setColor(COLORS[0]!); setIcon(ICONS[0]!.name);
      requestAnimationFrame(() => firstInputRef.current?.focus());
    }
  }, [open]);

  // Escape-to-close.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  const trimmedName = name.trim();
  const canSubmit   = trimmedName.length > 0;

  const submit = (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!canSubmit) return;
    onAdd({ name: trimmedName, blurb: blurb.trim(), color, icon });
    onClose();
  };

  return (
    <div
      className="modal-backdrop"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="add-subject-title"
    >
      <form className="modal" onSubmit={submit}>
        <header className="modal__head">
          <h2 id="add-subject-title" className="modal__title">Add subject</h2>
          <button
            type="button"
            className="modal__close"
            onClick={onClose}
            aria-label="Close"
          >
            <X size={16} />
          </button>
        </header>

        <div className="modal__body stack-md">
          <label className="field">
            <span className="field__label">Subject name</span>
            <input
              ref={firstInputRef}
              className="field__input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Organic Chemistry"
              maxLength={48}
              autoComplete="off"
            />
          </label>

          <label className="field">
            <span className="field__label">Description <span className="muted small">(optional)</span></span>
            <input
              className="field__input"
              value={blurb}
              onChange={(e) => setBlurb(e.target.value)}
              placeholder="One-line blurb shown on the subject card"
              maxLength={96}
              autoComplete="off"
            />
          </label>

          <div className="field">
            <span className="field__label">Color</span>
            <div className="swatch-row" role="radiogroup" aria-label="Subject color">
              {COLORS.map(c => (
                <button
                  key={c}
                  type="button"
                  role="radio"
                  aria-checked={color === c}
                  aria-label={`Color ${c}`}
                  className={'swatch' + (color === c ? ' swatch--on' : '')}
                  style={{ background: c }}
                  onClick={() => setColor(c)}
                />
              ))}
            </div>
          </div>

          <div className="field">
            <span className="field__label">Icon</span>
            <div className="swatch-row" role="radiogroup" aria-label="Subject icon">
              {ICONS.map(({ name: n, Icon }) => (
                <button
                  key={n}
                  type="button"
                  role="radio"
                  aria-checked={icon === n}
                  aria-label={`Icon ${n}`}
                  className={'icon-swatch' + (icon === n ? ' icon-swatch--on' : '')}
                  onClick={() => setIcon(n)}
                >
                  <Icon size={16} />
                </button>
              ))}
            </div>
          </div>
        </div>

        <footer className="modal__foot">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button
            type="submit"
            variant="primary"
            disabled={!canSubmit}
            trailing={<Kbd>↩</Kbd>}
          >
            Add subject
          </Button>
        </footer>
      </form>
    </div>
  );
}
