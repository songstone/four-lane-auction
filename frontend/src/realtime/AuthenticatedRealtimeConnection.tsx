import { useEffect } from 'react';
import { useAuth } from '../features/auth/useAuth';
import { retainConnection } from './client';

/** USER keeps one shared connection and personal queue throughout the app. */
export function AuthenticatedRealtimeConnection() {
  const { status, member } = useAuth();
  const enabled = status === 'authenticated' && member?.role === 'USER';
  useEffect(() => {
    if (enabled) return retainConnection();
  }, [enabled]);
  return null;
}
