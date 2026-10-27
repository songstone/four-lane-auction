import { Button } from './Button';

export function Toast({ message, tone = 'info', onDismiss }: {
  message: string;
  tone?: 'info' | 'error';
  onDismiss: () => void;
}) {
  return (
    <div role={tone === 'error' ? 'alert' : 'status'}
      className={`flex items-center justify-between gap-4 rounded-lg border border-line p-4 shadow-lg ${tone === 'error' ? 'bg-danger-soft text-danger' : 'bg-surface text-ink'}`}>
      <p className="text-sm">{message}</p>
      <Button variant="secondary" onClick={onDismiss} aria-label="알림 닫기">닫기</Button>
    </div>
  );
}
