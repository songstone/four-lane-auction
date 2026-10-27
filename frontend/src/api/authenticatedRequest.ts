import { forgetSession, refreshSession, requestAccessToken } from '../features/auth/authStore';

/** Wraps generated functions; transport and serialization remain in Orval's generated code. */
export async function authenticatedRequest<T extends { status: number }>(
  request: (options: RequestInit) => Promise<T>,
  options: { public?: boolean; signal?: AbortSignal } = {},
): Promise<T> {
  const send = (token: string | null) => request({
    credentials: 'same-origin', signal: options.signal,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  const attempted = requestAccessToken();
  let response = await send(attempted);
  if (response.status !== 401 || attempted === null) return response;
  // A late 401 for an older token joins the result already installed by another request.
  if (attempted !== requestAccessToken() || await refreshSession()) {
    const current = requestAccessToken();
    if (current !== null) {
      response = await send(current);
      if (response.status !== 401) return response;
      forgetSession();
    }
  }
  return options.public ? send(null) : response;
}
