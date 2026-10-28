import { useSyncExternalStore } from 'react';
import { connectionSnapshot, subscribeConnectionStatus } from './client';

export function useConnectionStatus() {
  return useSyncExternalStore(subscribeConnectionStatus, connectionSnapshot);
}
