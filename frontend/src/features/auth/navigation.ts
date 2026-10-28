export function safeReturnTo(value: string | null) {
  return value !== null && ['/me', '/me/items', '/items/new', '/admin/items', '/admin/bid-policies'].includes(value) ? value : '/';
}
export function navigate(path: string) { window.location.hash = path; }
export function loginHref(returnTo = '/') { return `#/login?returnTo=${encodeURIComponent(safeReturnTo(returnTo))}`; }
