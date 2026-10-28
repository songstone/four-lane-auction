import { skipToken, useQuery, type QueryClient } from '@tanstack/react-query';
import type { PublicLaneSnapshot } from '../api/generated/models';

export function applySnapshot(client: QueryClient, snapshot: PublicLaneSnapshot) {
  client.setQueryData<PublicLaneSnapshot>(['lane', snapshot.laneId], previous => {
    if (previous?.epoch === snapshot.epoch && previous.seq > snapshot.seq) return previous;
    // READY relineup retains seq=0. Equal sequence snapshots must replace the whole value.
    // The caller owns request cancellation and generation/identity guards, including new epochs.
    return snapshot;
  });
}

export function useLaneSnapshot(laneId: number) {
  return useQuery<PublicLaneSnapshot>({
    queryKey: ['lane', laneId], queryFn: skipToken, staleTime: Infinity,
    refetchOnMount: false, refetchOnWindowFocus: false, refetchOnReconnect: false,
  });
}
