import { useInfiniteQuery } from '@tanstack/react-query';
import { listMyItems } from './generated/sessions';
import { authenticatedRequest } from './authenticatedRequest';
import { responseError } from './errorMessages';

export function useMyItems(memberId: number | undefined) {
  return useInfiniteQuery({
    queryKey: ['myItems', memberId], enabled: memberId !== undefined,
    queryFn: async ({ pageParam, signal }) => {
      const response = await authenticatedRequest(options => listMyItems({ size: 20,
        ...(pageParam === null ? {} : { cursor: pageParam }) }, options), { signal });
      if (response.status !== 200) throw responseError(response.data);
      return response.data;
    },
    initialPageParam: null as string | null,
    getNextPageParam: page => page.nextCursor ?? undefined,
    staleTime: 0, refetchOnMount: 'always', refetchOnWindowFocus: true, refetchOnReconnect: true, retry: false,
  });
}
