import { Client, ReconnectionTimeMode, Versions } from '@stomp/stompjs';
import { bootstrap, forgetSession, refreshSession, requestConnectionAuth, subscribeAuth } from '../features/auth/authStore';

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected' | 'reconnecting';

interface ConnectionState {
  status: ConnectionStatus;
  identity: 'anonymous' | 'authenticated' | null;
  error: string | null;
}
let state: ConnectionState = { status: 'connecting', identity: null, error: null };
const listeners = new Set<() => void>();
let client: Client | null = null;
let stopping: Promise<void> | null = null;
let unsubscribeAuth: (() => void) | null = null;
let watchedIdentity = 'anonymous';
let fatal = false;

function publish(status: ConnectionStatus, identity: ConnectionState['identity'] = null, error: string | null = null) {
  if (state.status === status && state.identity === identity && state.error === error) return;
  state = { status, identity, error };
  listeners.forEach((listener) => listener());
}

function stopClient(error: string | null = null) {
  if (client === null) return;
  const previous = client;
  client = null;
  publish('disconnected', null, error);
  // Await actual closure before a replacement or StrictMode remount opens a socket.
  stopping = previous.deactivate().finally(() => {
    stopping = null;
    reconcile();
  });
}

function createClient(): Client {
  const url = new URL('/ws', window.location.href);
  url.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  let attempts = 0;
  let attemptedToken: string | null = null;
  let attemptedIdentity = '';
  let attemptedRole: string | null = null;
  let rejectedToken: string | null = null;
  let recoveryAttempted = false;
  const instance = new Client({
    brokerURL: url.toString(),
    stompVersions: new Versions(['1.2']),
    connectHeaders: {},
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    connectionTimeout: 10_000,
    reconnectDelay: 1_000,
    maxReconnectDelay: 30_000,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    debug: () => {},
  });
  instance.beforeConnect = async () => {
    if (client !== instance) return;
    publish(attempts === 0 ? 'connecting' : 'reconnecting');
    if (attempts++ > 0) {
      await new Promise<void>((resolve) => window.setTimeout(resolve, Math.floor(Math.random() * 500)));
    }
    await bootstrap(); // Share the app's existing restoration, including its Web Lock.
    if (client !== instance) return;
    const before = requestConnectionAuth();
    const recovering = rejectedToken !== null;
    const needsRefresh = before.token !== null && (
      before.token === rejectedToken || !Number.isFinite(before.expiresAt) || before.expiresAt <= Date.now() + 60_000);
    try {
      if (needsRefresh) await refreshSession();
    } catch {
      if (client === instance && before.identity === requestConnectionAuth().identity) forgetSession();
    }
    if (client !== instance) return; // A late refresh must never resurrect an older identity.
    const current = requestConnectionAuth();
    if (recovering && current.token === rejectedToken) {
      forgetSession(); // Do not CONNECT again with the same rejected token.
      return;
    }
    recoveryAttempted ||= recovering;
    rejectedToken = null;
    attemptedToken = current.token;
    attemptedIdentity = current.identity;
    attemptedRole = current.role;
    instance.connectHeaders = current.token ? { Authorization: `Bearer ${current.token}` } : {};
  };
  // A WebSocket open alone is insufficient: only STOMP CONNECTED marks success.
  instance.onConnect = () => {
    if (client !== instance) return;
    if (attemptedIdentity !== requestConnectionAuth().identity) { stopClient(); return; }
    recoveryAttempted = false;
    if (attemptedRole === 'USER') {
      // Notification creation, types and UI belong to M3-06; establish the authorized subscription only.
      instance.subscribe('/user/queue/notifications', () => {}, { id: 'notifications' });
    }
    publish('connected', attemptedToken ? 'authenticated' : 'anonymous');
  };
  const disconnected = () => { if (client === instance) publish('disconnected'); };
  instance.onWebSocketClose = disconnected;
  instance.onWebSocketError = disconnected;
  instance.onStompError = (frame) => {
    if (client !== instance) return;
    const reason = frame.headers.message;
    publish('disconnected', null, reason);
    if (reason === 'TOKEN_EXPIRED' || reason === 'TOKEN_INVALID') {
      if (recoveryAttempted || attemptedToken === null) forgetSession();
      else rejectedToken = attemptedToken;
    } else {
      fatal = true; // A rejected subscription/frame is a client bug, not a reconnect loop.
      stopClient(reason);
    }
  };
  return instance;
}

function reconcile() {
  if (listeners.size > 0 && !fatal) {
    if (client !== null || stopping !== null) return;
    client = createClient();
    client.activate();
  } else {
    stopClient();
  }
}

function authChanged() {
  const identity = requestConnectionAuth().identity;
  if (watchedIdentity !== identity) {
    watchedIdentity = identity;
    fatal = false;
    stopClient();
  }
  reconcile();
}

export function subscribeConnection(listener: () => void) {
  if (unsubscribeAuth === null) {
    watchedIdentity = requestConnectionAuth().identity;
    unsubscribeAuth = subscribeAuth(authChanged);
  }
  listeners.add(listener);
  reconcile();
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) {
      unsubscribeAuth?.();
      unsubscribeAuth = null;
      fatal = false;
    }
    reconcile();
  };
}

export function connectionSnapshot() {
  return state;
}
