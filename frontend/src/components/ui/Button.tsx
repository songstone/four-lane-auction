import type { ButtonHTMLAttributes } from 'react';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary';
};

export function Button({ variant = 'primary', className = '', type = 'button', ...props }: ButtonProps) {
  const color = variant === 'primary'
    ? 'bg-brand text-surface hover:bg-brand-hover'
    : 'border border-line bg-surface text-ink hover:bg-canvas';
  return (
    <button
      type={type}
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand disabled:pointer-events-none disabled:opacity-50 ${color} ${className}`}
      {...props}
    />
  );
}
