export function Spinner({ label = '불러오는 중' }: { label?: string }) {
  return (
    <span role="status" className="inline-flex items-center gap-2 text-sm text-muted">
      <span aria-hidden="true" className="size-4 animate-spin rounded-full border-2 border-line border-t-brand motion-reduce:animate-none" />
      <span>{label}</span>
    </span>
  );
}
