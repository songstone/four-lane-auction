export function safeReturnTo(value: string | null) {
  return value === '/me' ? '/me' : '/';
}
export function navigate(path: string) { window.location.hash = path; }
export function loginHref(returnTo = '/') { return `#/login?returnTo=${encodeURIComponent(safeReturnTo(returnTo))}`; }
