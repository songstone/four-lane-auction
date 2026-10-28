import { useQuery } from '@tanstack/react-query';
import { getAdminSession } from './generated/sessions';
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
