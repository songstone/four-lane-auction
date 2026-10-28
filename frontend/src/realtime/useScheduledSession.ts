import { useCallback, useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { usePublicSession } from '../api/publicSessionQueries';
import { requestConnectionAuth } from '../features/auth/authStore';
import { retainConnection, subscribeLaneTopics } from './client';
import { useConnectionStatus } from './useConnectionStatus';
import { applySnapshot } from './laneSnapshots';

/** Scheduled-only transport. Event application/gap and beacon repair follow in M2-05. */
export function useScheduledSession(sessionId: number, identity: string) {
  const queryClient = useQueryClient();
  const query = usePublicSession(sessionId, identity);
  const { refetch } = query;
  const connection = useConnectionStatus();
  const activeSubscription = useRef<symbol | null>(null);
  const laneKey = query.data?.lanes.map(lane => lane.laneId).join(',') ?? '';

  const refresh = useCallback(async (cancelRefetch = false) => {
    const subscription = activeSubscription.current;
    const result = await refetch({ cancelRefetch });
    if (!result.isError && result.data && subscription !== null &&
      activeSubscription.current === subscription && requestConnectionAuth().identity === identity) {
      result.data.lanes.forEach(lane => applySnapshot(queryClient, lane.snapshot));
    }
  }, [identity, queryClient, refetch]);

  useEffect(() => {
    if (connection.status !== 'connected' || !laneKey) return;
    // No scheduled lineup events exist. Keep real subscriptions; the live consumer belongs to M2-05.
    const unsubscribe = subscribeLaneTopics(laneKey.split(',').map(Number), () => {});
    if (!unsubscribe) return;
    activeSubscription.current = Symbol();
    // Cancel any pre-subscription read, then establish a fresh baseline after SUBSCRIBE.
    void refresh(true);
    return () => {
      activeSubscription.current = null;
      unsubscribe();
      void queryClient.cancelQueries({ queryKey: ['session', sessionId], exact: true });
    };
  }, [connection.status, connection.generation, laneKey, refresh, queryClient, sessionId]);

  useEffect(() => {
    const reloadVisible = () => { if (document.visibilityState === 'visible') void refresh(); };
    const timer = window.setInterval(reloadVisible, 60_000);
    document.addEventListener('visibilitychange', reloadVisible);
    window.addEventListener('online', reloadVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', reloadVisible);
      window.removeEventListener('online', reloadVisible);
      void queryClient.cancelQueries({ queryKey: ['session', sessionId], exact: true });
    };
  }, [refresh, queryClient, sessionId]);

  // Cleanup order matters: send lane UNSUBSCRIBE before releasing the last socket owner.
  useEffect(() => retainConnection(), []);

  return { query, connection, refresh };
}
