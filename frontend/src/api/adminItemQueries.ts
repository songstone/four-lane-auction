import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { getAdminItem, listAdminItems } from './generated/sessions';
import type { ListAdminItemsStage } from './generated/models';
import { authenticatedRequest } from './authenticatedRequest';
import { ApiRequestError, responseError } from './errorMessages';
import { requestConnectionAuth } from '../features/auth/authStore';

export function adminRequest<T extends { status: number }>(request: (options: RequestInit) => Promise<T>, signal?: AbortSignal, identity = requestConnectionAuth().identity) {
  return authenticatedRequest(options => {
    const current = requestConnectionAuth();
    if (current.identity !== identity || current.role !== 'ADMIN') throw new ApiRequestError('AUTH_SESSION_CHANGED', '');
    return request(options);
  }, { signal });
}

export function useAdminItems(memberId: number | undefined, stage: ListAdminItemsStage) {
  return useInfiniteQuery({
    queryKey: ['adminItems', memberId, stage], enabled: memberId !== undefined,
    queryFn: async ({ pageParam, signal }) => {
      const response = await adminRequest(options => listAdminItems({ stage, size: 20,
        ...(pageParam === null ? {} : { cursor: pageParam }) }, options), signal);
      if (response.status !== 200) throw responseError(response.data);
      return response.data;
    },
    initialPageParam: null as string | null, getNextPageParam: page => page.nextCursor ?? undefined,
    staleTime: 0, refetchOnMount: 'always', refetchOnWindowFocus: true, refetchOnReconnect: true, retry: false,
  });
}

export function useAdminItem(memberId: number | undefined, itemId: number) {
  return useQuery({
    queryKey: ['adminItem', memberId, itemId], enabled: memberId !== undefined,
    queryFn: async ({ signal }) => {
      const response = await adminRequest(options => getAdminItem(itemId, options), signal);
      if (response.status !== 200) throw responseError(response.data);
      return response.data;
    },
    staleTime: 0, refetchOnMount: 'always', refetchOnWindowFocus: true, refetchOnReconnect: true, retry: false,
  });
}
