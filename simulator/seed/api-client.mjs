// This Node client uses only the existing REST contract. It never retries an
// uncertain write; only an explicit pre-handler authentication rejection can
// refresh once and replay the request once.
const API_CODES = new Set([
  'INVALID_REQUEST', 'INVALID_BID_AMOUNT', 'UNSUPPORTED_IMAGE_TYPE',
  'UNAUTHENTICATED', 'INVALID_CREDENTIALS', 'FORBIDDEN', 'BIDDER_SUSPENDED',
  'SELF_BID_FORBIDDEN', 'NOT_TRADE_BUYER', 'NOT_ITEM_SELLER', 'NOT_FOUND',
  'SESSION_NOT_FOUND', 'LANE_NOT_FOUND', 'LOT_NOT_FOUND', 'ITEM_NOT_FOUND',
  'TRADE_NOT_FOUND', 'NOTIFICATION_NOT_FOUND', 'IMAGE_NOT_FOUND',
  'EMAIL_ALREADY_EXISTS', 'LOT_MISMATCH', 'PRICE_CHANGED', 'ALREADY_HIGHEST_BIDDER',
  'IMAGE_TOO_LARGE', 'IDEMPOTENCY_KEY_REUSED', 'LANE_NOT_RUNNING', 'LANE_NOT_PAUSED',
  'LOT_NOT_BIDDING', 'TRADE_NOT_AWAITING_PAYMENT', 'PAYMENT_DEADLINE_PASSED',
  'RELIST_NOT_ALLOWED', 'ITEM_NOT_PENDING_REVIEW', 'ITEM_NOT_ASSIGNABLE',
  'SESSION_NOT_SCHEDULED', 'SCHEDULED_START_IN_PAST', 'SESSION_NOT_RUNNING',
  'SESSION_START_CONDITION_NOT_MET', 'INTERNAL_ERROR', 'BID_RESULT_UNKNOWN',
  'LANE_OVERLOADED',
]);
const LOCAL_CODES = new Set([
  'HTTP_ERROR', 'INVALID_CONFIGURATION', 'INVALID_RESPONSE', 'INVALID_ROLE',
  'NETWORK_ERROR', 'REQUEST_TIMEOUT', 'REDIRECT_REFUSED', 'NOT_LOGGED_IN',
]);

export class ApiError extends Error {
  constructor(status, code) {
    const safeStatus = Number.isInteger(status) && status >= 0 && status <= 599 ? status : 0;
    const safeCode = API_CODES.has(code) || LOCAL_CODES.has(code) ? code : 'HTTP_ERROR';
    super(`API request failed (${safeStatus} ${safeCode})`);
    this.name = 'ApiError';
    this.status = safeStatus;
    this.code = safeCode;
  }
}

function safePath(path) {
  const pathname = path.split('?')[0];
  if (/^\/api\/(?:auth\/(?:signup|login|refresh|logout)|me(?:\/items)?|items|admin\/(?:items|sessions|bid-policies)|sessions)$/.test(pathname)) return pathname;
  if (/^\/api\/(?:admin\/)?(?:items|sessions|lots)\/[1-9]\d*(?:\/(?:approve|reject))?$/.test(pathname)) return pathname;
  if (/^\/api\/admin\/sessions\/[1-9]\d*\/lanes\/[1-4]\/lots$/.test(pathname)) return pathname;
  if (/^\/api\/images\/[a-f0-9]{32}$/.test(pathname)) return '/api/images/{storageKey}';
  return '/api/[redacted]';
}

function contentTypeOf(response) {
  const value = (response.headers.get('content-type') ?? '').split(';')[0].trim().toLowerCase();
  return /^[a-z0-9!#$&^_.+-]+\/[a-z0-9!#$&^_.+-]+$/.test(value) ? value : '';
}

function copyMember(member, expectedRole) {
  if (!member || !Number.isSafeInteger(member.memberId) || member.memberId <= 0
    || typeof member.email !== 'string' || typeof member.nickname !== 'string'
    || typeof member.bidSuspended !== 'boolean' || !Number.isSafeInteger(member.warningCount)
    || member.warningCount < 0) throw new ApiError(200, 'INVALID_RESPONSE');
  if (!['USER', 'ADMIN'].includes(member.role) || member.role !== expectedRole) throw new ApiError(200, 'INVALID_ROLE');
  return Object.freeze({ memberId: member.memberId, email: member.email, nickname: member.nickname,
    role: member.role, bidSuspended: member.bidSuspended, warningCount: member.warningCount });
}

function refreshCookie(headers) {
  // getSetCookie is provided by Node 24. Never split Set-Cookie on commas:
  // cookie expiry dates may themselves contain commas.
  const values = typeof headers.getSetCookie === 'function' ? headers.getSetCookie() : [];
  for (const value of values) {
    const match = /^refreshToken=([A-Za-z0-9_-]{43});/i.exec(value);
    if (match) return `refreshToken=${match[1]}`;
  }
  throw new ApiError(200, 'INVALID_RESPONSE');
}

export class ApiClient {
  #base;
  #fetch;
  #timeout;
  #onResult;
  #accessToken = null;
  #cookie = null;
  #member = null;
  #expectedRole = null;

  constructor(baseUrl, { fetchImpl = globalThis.fetch, timeoutMs = 30_000, onResult = () => {} } = {}) {
    try {
      this.#base = new URL(baseUrl);
      if (!['http:', 'https:'].includes(this.#base.protocol) || this.#base.username || this.#base.password
        || this.#base.pathname !== '/' || this.#base.search || this.#base.hash
        || typeof fetchImpl !== 'function' || typeof onResult !== 'function'
        || !Number.isSafeInteger(timeoutMs) || timeoutMs < 1) throw new Error();
    } catch { throw new ApiError(0, 'INVALID_CONFIGURATION'); }
    this.#fetch = fetchImpl;
    this.#timeout = timeoutMs;
    this.#onResult = onResult;
  }

  get member() { return this.#member; }

  async login({ email, password }, expectedRole) {
    if (!['USER', 'ADMIN'].includes(expectedRole)) throw new ApiError(0, 'INVALID_ROLE');
    if (this.#accessToken || this.#cookie) throw new ApiError(0, 'INVALID_CONFIGURATION');
    const result = await this.#exchange('POST', '/api/auth/login', { json: { email, password }, expected: [200] });
    this.#acceptAuthentication(result, expectedRole);
    return this.#member;
  }

  async request(method, path, { json, form, auth = false, expected = [200], binary = false } = {}) {
    const options = { json, form, auth, expected, binary };
    try {
      return this.#publicResult(await this.#exchange(method, path, options));
    } catch (error) {
      if (!(error instanceof ApiError) || !auth || error.status !== 401
        || error.code !== 'UNAUTHENTICATED' || !this.#cookie) throw error;
      const refreshed = await this.#exchange('POST', '/api/auth/refresh', { expected: [200] }, this.#cookie);
      this.#acceptAuthentication(refreshed, this.#expectedRole);
      // Outside the catch above: a second 401 stops, without a second refresh.
      return this.#publicResult(await this.#exchange(method, path, options));
    }
  }

  async logout() {
    try {
      if (!this.#cookie) return { status: 204, data: null, contentType: '' };
      return this.#publicResult(await this.#exchange('POST', '/api/auth/logout', { expected: [204] }, this.#cookie));
    } finally {
      this.#accessToken = null;
      this.#cookie = null;
      this.#member = null;
      this.#expectedRole = null;
    }
  }

  #acceptAuthentication(result, expectedRole) {
    // Retain a valid rotated cookie even when another response field is invalid,
    // so finally/logout can revoke the server-side session that was just issued.
    this.#cookie = refreshCookie(result.headers);
    const data = result.data;
    if (!data || typeof data.accessToken !== 'string' || !/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(data.accessToken)
      || typeof data.accessTokenExpiresAt !== 'string' || !Number.isFinite(Date.parse(data.accessTokenExpiresAt))) {
      throw new ApiError(result.status, 'INVALID_RESPONSE');
    }
    const member = copyMember(data.member, expectedRole);
    if (this.#member && member.memberId !== this.#member.memberId) throw new ApiError(200, 'INVALID_RESPONSE');
    this.#member = member;
    this.#expectedRole = expectedRole;
    this.#accessToken = data.accessToken;
  }

  #publicResult({ status, data, contentType }) { return { status, data, contentType }; }

  #report(method, path, status, code) {
    // Reporting is observational. Never expose a callback exception containing
    // captured credentials, and never turn an observed committed response into
    // a request failure because a progress callback failed.
    try { this.#onResult({ method, path: safePath(path), status, code }); } catch { /* no secrets */ }
  }

  async #exchange(method, path, { json, form, auth = false, expected = [200], binary = false }, cookie = null) {
    if (typeof method !== 'string' || !/^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)$/.test(method)
      || typeof path !== 'string' || !path.startsWith('/api/') || path.includes('\\') || path.includes('#')
      || json !== undefined && form !== undefined || form !== undefined && !(form instanceof FormData)
      || !Array.isArray(expected) || expected.length === 0 || expected.some(s => !Number.isInteger(s) || s < 200 || s > 299)) {
      throw new ApiError(0, 'INVALID_CONFIGURATION');
    }
    const url = new URL(path, this.#base);
    if (url.origin !== this.#base.origin || !url.pathname.startsWith('/api/')) throw new ApiError(0, 'INVALID_CONFIGURATION');
    if (auth && !this.#accessToken) throw new ApiError(0, 'NOT_LOGGED_IN');
    const headers = { Accept: binary ? 'image/png, image/jpeg, image/webp' : 'application/json' };
    if (auth) headers.Authorization = `Bearer ${this.#accessToken}`;
    if (cookie) headers.Cookie = cookie;
    let body;
    if (json !== undefined) {
      headers['Content-Type'] = 'application/json';
      try { body = JSON.stringify(json); } catch { throw new ApiError(0, 'INVALID_CONFIGURATION'); }
    } else if (form !== undefined) {
      // A new FormData for each attempt avoids reusing a consumed Request body.
      // Blob/File contents are immutable and can be read again after a 401.
      body = new FormData();
      for (const [key, value] of form) {
        if (typeof value === 'string') body.append(key, value);
        else body.append(key, value, value.name);
      }
    }
    const signal = AbortSignal.timeout(this.#timeout);
    let response;
    try {
      response = await this.#fetch(url, { method, headers, body, signal, redirect: 'manual', credentials: 'omit' });
    } catch {
      const code = signal.aborted ? 'REQUEST_TIMEOUT' : 'NETWORK_ERROR';
      this.#report(method, path, 0, code);
      throw new ApiError(0, code);
    }
    if (response.status >= 300 && response.status <= 399) {
      this.#report(method, path, response.status, 'REDIRECT_REFUSED');
      throw new ApiError(response.status, 'REDIRECT_REFUSED');
    }
    const contentType = contentTypeOf(response);
    const isJson = contentType === 'application/json' || contentType.endsWith('+json');
    if (!expected.includes(response.status)) {
      let code = 'HTTP_ERROR';
      if (isJson) {
        try {
          const data = await response.json();
          if (API_CODES.has(data?.error?.code)) code = data.error.code;
        } catch { /* error response bodies and parse errors never escape */ }
      }
      this.#report(method, path, response.status, code);
      throw new ApiError(response.status, code);
    }
    let data = null;
    try {
      if (response.status !== 204) {
        if (binary) data = new Uint8Array(await response.arrayBuffer());
        else {
          if (!isJson) throw new Error();
          data = await response.json();
        }
      }
    } catch {
      const code = signal.aborted ? 'REQUEST_TIMEOUT' : 'INVALID_RESPONSE';
      this.#report(method, path, response.status, code);
      throw new ApiError(response.status, code);
    }
    this.#report(method, path, response.status, null);
    return { status: response.status, data, contentType, headers: response.headers };
  }
}
