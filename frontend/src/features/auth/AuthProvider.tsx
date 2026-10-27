import { createContext, useEffect, useSyncExternalStore } from 'react';
import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { MemberResponse } from '../../api/generated/models';
import { getMe } from '../../api/generated/sessions';
import { authenticatedRequest } from '../../api/authenticatedRequest';
import { responseError } from '../../api/errorMessages';
import { authSnapshot, bootstrap, subscribeAuth } from './authStore';

interface AuthContextValue {
  status: ReturnType<typeof authSnapshot>['status'];
  member: MemberResponse | null;
  error: unknown;
}
export const AuthContext = createContext<AuthContextValue | null>(null);

export async function fetchMe(signal?: AbortSignal) {
  const response = await authenticatedRequest((options) => getMe(options), { signal });
  if (response.status !== 200) throw responseError(response.data);
  return response.data;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const state = useSyncExternalStore(subscribeAuth, authSnapshot);
  const me = useQuery({
    queryKey: ['me'], queryFn: ({ signal }) => fetchMe(signal), enabled: state.status === 'authenticated',
    staleTime: Infinity, refetchOnWindowFocus: false, refetchOnReconnect: false, refetchOnMount: false, retry: false,
  });
  useEffect(() => { void bootstrap(); }, []);
  return <AuthContext value={{ status: state.status, member: state.status === 'authenticated' ? me.data ?? null : null,
    error: state.error }}>{children}</AuthContext>;
}
