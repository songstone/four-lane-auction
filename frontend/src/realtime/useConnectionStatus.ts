import { useSyncExternalStore } from 'react';
import { connectionSnapshot, subscribeConnection } from './client';

export function useConnectionStatus() {
  return useSyncExternalStore(subscribeConnection, connectionSnapshot);
}
