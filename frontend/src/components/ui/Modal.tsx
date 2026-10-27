import { useEffect, useId, useRef } from 'react';
import type { ReactNode } from 'react';
import { Button } from './Button';

export function Modal({ open, title, children, onClose }: {
  open: boolean;
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const element = dialog.current;
    if (open && element && !element.open) element.showModal();
    if (!open && element?.open) element.close();
  }, [open]);

  return (
    <dialog ref={dialog} aria-labelledby={titleId} onClose={() => { if (open) onClose(); }}
      onCancel={(event) => { event.preventDefault(); onClose(); }}
      className="fixed inset-0 m-auto w-full max-w-lg rounded-xl border border-line bg-surface p-6 text-ink shadow-xl backdrop:bg-overlay">
      <div className="mb-5 flex items-center justify-between gap-4">
        <h2 id={titleId} className="text-lg font-semibold">{title}</h2>
        <Button variant="secondary" onClick={onClose} aria-label="대화상자 닫기">닫기</Button>
      </div>
      {children}
    </dialog>
  );
}
