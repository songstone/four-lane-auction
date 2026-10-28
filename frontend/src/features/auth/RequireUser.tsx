import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { useAuth } from './useAuth';
import { bootstrap } from './authStore';
import { loginHref } from './navigation';
import { apiErrorMessage } from '../../api/errorMessages';
import { Spinner } from '../../components/ui/Spinner';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';

export function RequireUser({ returnTo, children }: { returnTo: string; children: ReactNode }) {
  const auth = useAuth();
  useEffect(() => {
    if (auth.status === 'anonymous' && window.location.hash.split('?')[0] === `#${returnTo}`) {
      window.location.hash = loginHref(returnTo).slice(1);
    }
  }, [auth.status, returnTo]);
  if (auth.status === 'error') return <main className="mx-auto max-w-2xl px-6 py-12"><Card>
    <p role="alert" className="text-danger">{apiErrorMessage(auth.error)}</p>
    <Button className="mt-4" onClick={() => { void bootstrap(true); }}>로그인 확인 다시 시도</Button>
  </Card></main>;
  if (auth.status !== 'authenticated' || !auth.member) return <main className="mx-auto max-w-2xl px-6 py-12"><Spinner label="로그인 확인 중" /></main>;
  if (auth.member.role !== 'USER') return <main className="mx-auto max-w-2xl px-6 py-12"><Card>
    <h1 className="text-2xl font-bold">접근 권한이 없습니다</h1>
    <p role="alert" className="mt-3 text-muted">출품 등록과 내 출품 목록은 일반 회원 계정으로 이용할 수 있습니다.</p>
    <a href="#/" className="mt-6 inline-block text-brand">경매 홈으로</a>
  </Card></main>;
  return children;
}
