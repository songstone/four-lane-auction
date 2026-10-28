import { serverClock } from '../features/time/serverClock';
import { useInfiniteQuery } from '@tanstack/react-query';
import type { SessionStatus } from './generated/models';
import { listSessions } from './generated/sessions';
import { ApiRequestError } from './errorMessages';
import { authenticatedRequest } from './authenticatedRequest';

export function useSessionList(status: SessionStatus) {
  return useInfiniteQuery({
    queryKey: ['sessions', status],
    queryFn: async ({ pageParam, signal }) => {
      const response = await authenticatedRequest((options) => listSessions({
        status,
        size: 20,
        ...(pageParam === null ? {} : { cursor: pageParam }),
      }, options), { signal, public: true });
      if (response.status !== 200) {
        throw new ApiRequestError(response.data.error.code, response.data.error.message);
      }
      serverClock.sample(response.data.serverTime);
      return response.data;
    },
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    refetchInterval: 30_000,
    refetchOnWindowFocus: 'always',
    refetchOnReconnect: 'always',
    refetchOnMount: 'always',
    retry: false,
  });
}

export type SessionListQuery = ReturnType<typeof useSessionList>;
