import type { QueryClient } from '@tanstack/react-query';
import type { AuthResponse, LoginRequest, MemberResponse } from '../../api/generated/models';
import { login, logout, refresh } from '../../api/generated/sessions';
import { ApiRequestError, responseError } from '../../api/errorMessages';

type AuthStatus = 'loading' | 'authenticated' | 'anonymous' | 'error';
interface AuthState { status: AuthStatus; error: unknown }

// Credentials live only in this module's memory. Member data belongs to Query's ['me'] cache.
let token: string | null = null;
let expiresAt = 0;
let connectionMember: { id: number; role: string } | null = null;
let epoch = 0;
let state: AuthState = { status: 'loading', error: null };
let onMember: (member: MemberResponse | null) => void = () => {};
const listeners = new Set<() => void>();
let refreshPromise: Promise<boolean> | null = null;
let bootstrapPromise: Promise<void> | null = null;
let bootstrapped = false;

export function connectAuthQueryCache(client: QueryClient) {
  onMember = (member) => {
    const previous = client.getQueryData<MemberResponse>(['me']);
    if (!member || (previous && previous.memberId !== member.memberId)) {
      void client.cancelQueries({ queryKey: ['myItems'] });
      client.removeQueries({ queryKey: ['myItems'] });
      for (const key of ['adminItems', 'adminItem']) {
        void client.cancelQueries({ queryKey: [key] });
        client.removeQueries({ queryKey: [key] });
      }
      client.removeQueries({ queryKey: ['me'] });
    }
    if (member) client.setQueryData(['me'], member);
  };
}

function publish(status: AuthStatus, error: unknown = null) {
  state = { status, error };
  listeners.forEach((listener) => listener());
}

export function authSnapshot() { return state; }
export function subscribeAuth(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}
export function requestAccessToken() { return token; }
export function requestConnectionAuth() {
  return { token, expiresAt, role: connectionMember?.role ?? null,
    identity: connectionMember ? `${epoch}:${connectionMember.id}:${connectionMember.role}` : 'anonymous' };
}

function accept(data: AuthResponse) {
  token = data.accessToken;
  expiresAt = Date.parse(data.accessTokenExpiresAt);
  connectionMember = { id: data.member.memberId, role: data.member.role };
  onMember(data.member);
  publish('authenticated');
}

export function forgetSession() {
  epoch++;
  token = null;
  expiresAt = 0;
  connectionMember = null;
  onMember(null);
  publish('anonymous');
}

async function withAuthLock<T>(operation: () => Promise<T>): Promise<T> {
  if (!navigator.locks) throw new ApiRequestError('AUTH_SYNC_UNAVAILABLE', 'Web Locks unavailable');
  return navigator.locks.request('auth-refresh', operation);
}

export function refreshSession(): Promise<boolean> {
  if (refreshPromise) return refreshPromise;
  const intent = epoch;
  refreshPromise = withAuthLock(async () => {
    if (intent !== epoch) return false;
    let response = await refresh({ credentials: 'same-origin' });
    if (intent !== epoch) return false;
    // API 1.3: one retry after 401 while holding the cross-tab lock.
    if (response.status === 401) response = await refresh({ credentials: 'same-origin' });
    if (intent !== epoch) return false;
    if (response.status === 200) { accept(response.data); return true; }
    if (response.status === 401) { forgetSession(); return false; }
    throw responseError(response.data);
  }).finally(() => { refreshPromise = null; });
  return refreshPromise;
}

export function bootstrap(force = false): Promise<void> {
  if (bootstrapPromise) return bootstrapPromise;
  if (bootstrapped && !force) return Promise.resolve();
  publish('loading');
  bootstrapPromise = refreshSession().then(() => {}).catch((error: unknown) => {
    publish('error', error);
  }).finally(() => { bootstrapped = true; bootstrapPromise = null; });
  return bootstrapPromise;
}

export async function signIn(credentials: LoginRequest): Promise<void> {
  const intent = ++epoch;
  await withAuthLock(async () => {
    if (intent !== epoch) return;
    // Auth endpoints never receive an old bearer token, and never enter the 401 interceptor.
    const response = await login(credentials, { credentials: 'same-origin' });
    if (response.status !== 200) throw responseError(response.data);
    if (intent === epoch) accept(response.data);
  });
}

export async function signOut(): Promise<void> {
  forgetSession(); // API 3.4: forget the access token regardless of the server response.
  await withAuthLock(async () => {
    const response = await logout({ credentials: 'same-origin' });
    if (response.status !== 204) throw responseError(response.data);
  });
}
