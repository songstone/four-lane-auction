export function safeReturnTo(value: string | null) {
  return value !== null && ['/me', '/me/items', '/items/new', '/admin/items', '/admin/bid-policies', '/admin/sessions'].includes(value) ? value : value !== null && /^\/admin\/sessions\/[1-9]\d*\/lineup$/.test(value) && Number.isSafeInteger(Number(value.split('/')[3])) ? value : '/';
}
export function navigate(path: string) { window.location.hash = path; }
export function loginHref(returnTo = '/') { return `#/login?returnTo=${encodeURIComponent(safeReturnTo(returnTo))}`; }
