import { useQuery } from '@tanstack/react-query';
import { listBidPolicies } from './generated/sessions';
import { adminRequest } from './adminItemQueries';
import { responseError } from './errorMessages';

export function useBidPolicies(memberId: number | undefined) {
  return useQuery({
    queryKey: ['adminBidPolicies', memberId], enabled: memberId !== undefined,
    queryFn: async ({ signal }) => {
      const response = await adminRequest(options => listBidPolicies(options), signal);
      if (response.status !== 200) throw responseError(response.data);
      return response.data.items;
    },
    staleTime: 0, refetchOnMount: 'always', refetchOnWindowFocus: true, refetchOnReconnect: true, retry: false,
  });
}
