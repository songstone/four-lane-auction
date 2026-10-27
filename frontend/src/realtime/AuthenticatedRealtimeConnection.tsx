import { useEffect } from 'react';
import { useAuth } from '../features/auth/useAuth';
import { subscribeConnection } from './client';

/** USER keeps one shared connection even when the home diagnostic is unmounted. */
export function AuthenticatedRealtimeConnection() {
  const { status, member } = useAuth();
  const enabled = status === 'authenticated' && member?.role === 'USER';
  useEffect(() => {
    if (enabled) return subscribeConnection(() => {});
  }, [enabled]);
  return null;
}
