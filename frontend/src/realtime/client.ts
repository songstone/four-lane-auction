import { Client, ReconnectionTimeMode, Versions } from '@stomp/stompjs';

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected' | 'reconnecting';

let status: ConnectionStatus = 'connecting';
const listeners = new Set<() => void>();
let client: Client | null = null;
let stopping: Promise<void> | null = null;

function publish(next: ConnectionStatus) {
  if (status === next) return;
  status = next;
  listeners.forEach((listener) => listener());
}

function createClient(): Client {
  const url = new URL('/ws', window.location.href);
  url.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  let attempts = 0;
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
      // Add per-attempt jitter to the library's capped exponential backoff.
      await new Promise<void>((resolve) => window.setTimeout(resolve, Math.floor(Math.random() * 500)));
    }
  };
  // A WebSocket open alone is insufficient: only STOMP CONNECTED marks success.
  instance.onConnect = () => { if (client === instance) publish('connected'); };
  const disconnected = () => { if (client === instance) publish('disconnected'); };
  instance.onWebSocketClose = disconnected;
  instance.onWebSocketError = disconnected;
  instance.onStompError = disconnected;
  return instance;
}

function reconcile() {
  if (listeners.size > 0) {
    if (client !== null || stopping !== null) return;
    client = createClient();
    client.activate();
  } else if (client !== null) {
    const previous = client;
    client = null;
    publish('disconnected');
    // Await closure before a StrictMode remount can open another connection.
    stopping = previous.deactivate().finally(() => {
      stopping = null;
      reconcile();
    });
  }
}

export function subscribeConnection(listener: () => void) {
  listeners.add(listener);
  reconcile();
  return () => {
    listeners.delete(listener);
    reconcile();
  };
}

export function connectionSnapshot() {
  return status;
}
