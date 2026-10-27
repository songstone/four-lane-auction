import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchMe } from '../features/auth/AuthProvider';
import { useAuth } from '../features/auth/useAuth';
import { loginHref } from '../features/auth/navigation';
import { apiErrorMessage } from '../api/errorMessages';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Spinner } from '../components/ui/Spinner';

export function AccountPage() {
  const auth = useAuth();
  const query = useQuery({ queryKey: ['me'], queryFn: ({ signal }) => fetchMe(signal),
    enabled: auth.status === 'authenticated', staleTime: 0, refetchOnMount: 'always',
    refetchOnWindowFocus: false, refetchOnReconnect: false, retry: false });
  useEffect(() => {
    // Logout can already have navigated home before React processes hashchange.
    if (auth.status === 'anonymous' && window.location.hash.split('?')[0] === '#/me') {
      window.location.hash = loginHref('/me').slice(1);
    }
  }, [auth.status]);
  return <main className="mx-auto max-w-2xl px-6 py-12"><Card>
    <h1 className="text-2xl font-bold">내 정보</h1>
    {auth.status === 'loading' || query.isPending ? <div className="mt-6"><Spinner label="내 정보를 확인하는 중" /></div> :
      query.isError ? <div role="alert" className="mt-6 text-danger"><p>{apiErrorMessage(query.error)}</p>
        <Button variant="secondary" onClick={() => { void query.refetch(); }}>다시 조회</Button></div> :
      auth.member ? <dl className="mt-6 grid gap-4 text-sm">
        <div><dt className="text-muted">이메일</dt><dd className="mt-1 break-all">{auth.member.email}</dd></div>
        <div><dt className="text-muted">닉네임</dt><dd className="mt-1">{auth.member.nickname}</dd></div>
        <div><dt className="text-muted">역할</dt><dd className="mt-1">{auth.member.role === 'ADMIN' ? '운영자' : '일반 회원'}</dd></div>
        <div><dt className="text-muted">입찰 상태</dt><dd className="mt-1">{auth.member.bidSuspended ? '입찰 정지' : '입찰 정지 아님'}</dd></div>
        <div><dt className="text-muted">경고 수</dt><dd className="mt-1">{auth.member.warningCount}회</dd></div>
      </dl> : <p className="mt-6 text-muted">로그인 후 확인할 수 있습니다.</p>}
  </Card></main>;
}
