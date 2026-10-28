import { useQuery } from '@tanstack/react-query';
import { requestConnectionAuth } from '../features/auth/authStore';
import { serverClock } from '../features/time/serverClock';
import { authenticatedRequest } from './authenticatedRequest';
import { ApiRequestError, responseError } from './errorMessages';
import { getPublicLot, getPublicSession } from './generated/sessions';

export function usePublicSession(sessionId: number, identity: string) {
  return useQuery({
    queryKey: ['session', sessionId],
    queryFn: async ({ signal }) => {
      const response = await authenticatedRequest(options => getPublicSession(sessionId, options), { signal, public: true });
      if (signal.aborted || requestConnectionAuth().identity !== identity) {
        throw new ApiRequestError('AUTH_SESSION_CHANGED', 'Session changed');
      }
      if (response.status !== 200) throw responseError(response.data);
      // Only actual network receipt is a clock sample, never data read from Query's cache.
      serverClock.sample(response.data.serverTime);
      return response.data;
    },
    staleTime: Infinity,
    refetchOnMount: 'always',
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    retry: false,
  });
}

export function usePublicLot(lotId: number) {
  return useQuery({
    queryKey: ['lot', lotId],
    queryFn: async ({ signal }) => {
      const response = await authenticatedRequest(options => getPublicLot(lotId, options), { signal, public: true });
      if (response.status !== 200) throw responseError(response.data);
      return response.data;
    },
    staleTime: Infinity,
    refetchOnMount: 'always',
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    retry: false,
  });
}
