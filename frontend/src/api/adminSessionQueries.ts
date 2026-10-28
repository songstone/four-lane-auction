import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getAdminSession, replaceSessionLineup } from './generated/sessions';
import { adminRequest } from './adminItemQueries';
import { responseError } from './errorMessages';
export function useAdminSession(memberId: number | undefined, sessionId: number | null) {
  return useQuery({
    queryKey: ['adminSession', memberId, sessionId], enabled: memberId !== undefined && sessionId !== null,
    queryFn: async ({ signal }) => {
      if (sessionId === null) throw new Error('Session selection required');
      const response = await adminRequest(options => getAdminSession(sessionId, options), signal);
      if (response.status !== 200) throw responseError(response.data);
      return response.data;
    },
    staleTime: 0, refetchOnMount: 'always', refetchOnWindowFocus: true, refetchOnReconnect: true, retry: false,
  });
}

import { requestConnectionAuth } from '../features/auth/authStore';

export function useSaveSessionLineup(memberId: number, sessionId: number, laneNo: number) {
  const cache = useQueryClient();
  return useMutation({
    retry: false,
    mutationFn: async ({ itemIds, identity }: { itemIds: number[]; identity: string }) => {
      const response = await adminRequest(options => replaceSessionLineup(sessionId, laneNo, { itemIds }, options), undefined, identity);
      if (response.status !== 200) throw responseError(response.data);
      return response.data;
    },
    onSettled: async (_data, _error, variables) => {
      if (variables.identity !== requestConnectionAuth().identity) return;
      await Promise.all([
        cache.invalidateQueries({ queryKey: ['adminSession', memberId, sessionId] }),
        cache.invalidateQueries({ queryKey: ['adminItems', memberId] }),
        cache.invalidateQueries({ queryKey: ['adminItem', memberId] }),
        cache.invalidateQueries({ queryKey: ['sessions'] }),
      ]);
    },
  });
}
