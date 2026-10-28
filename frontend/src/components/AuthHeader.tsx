import { useMutation } from '@tanstack/react-query';
import { ApiRequestError, apiErrorMessage } from '../api/errorMessages';
import { useAuth } from '../features/auth/useAuth';
import { bootstrap, signOut } from '../features/auth/authStore';
import { loginHref, navigate, safeReturnTo } from '../features/auth/navigation';
import { Badge } from './ui/Badge';
import { Button } from './ui/Button';
import { Spinner } from './ui/Spinner';

export function AuthHeader() {
  const auth = useAuth();
  const logout = useMutation({ mutationFn: signOut });
  const returnTo = safeReturnTo(window.location.hash.slice(1).split('?')[0] ?? null);
  const logoutAction = () => { navigate('/'); logout.mutate(); };
  return <div className="border-b border-line bg-surface">
    <nav aria-label="계정 메뉴" className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-6 py-3">
      <a href="#/" className="font-semibold text-brand">경매 홈</a>
      <div className="flex flex-wrap items-center gap-3" data-testid="auth-status">
        {auth.status === 'loading' ? <Spinner label="로그인 확인 중" /> : auth.member ? <>
          <span>{auth.member.nickname}님</span>
          <Badge>{auth.member.role === 'ADMIN' ? '운영자' : '일반 회원'}</Badge>
          {auth.member.role === 'ADMIN' && <a href="#/admin/items" className="text-sm text-brand">출품 검수</a>}
          {auth.member.role === 'ADMIN' && <a href="#/admin/bid-policies" className="text-sm text-brand">호가 정책</a>}
          {auth.member.role === 'USER' && <><a href="#/items/new" className="text-sm text-brand">출품 등록</a>
            <a href="#/me/items" className="text-sm text-brand">내 출품 목록</a></>}
          <a href="#/me" className="text-sm text-brand">{auth.member.role === 'ADMIN' ? '운영자 계정' : '내 정보'}</a>
          <Button variant="secondary" disabled={logout.isPending} onClick={logoutAction}>로그아웃</Button>
        </> : <>
          <span className="text-sm text-muted">비로그인</span>
          <a href={loginHref(returnTo)} className="text-sm font-semibold text-brand">로그인</a>
          <a href={`#/signup?returnTo=${encodeURIComponent(returnTo)}`} className="text-sm text-brand">회원가입</a>
        </>}
      </div>
    </nav>
    {auth.status === 'error' && <div role="alert" className="mx-auto max-w-6xl px-6 pb-3 text-sm text-danger">
      {apiErrorMessage(auth.error)} <Button variant="secondary" onClick={() => { void bootstrap(true); }}>로그인 복구 다시 시도</Button>
    </div>}
    {logout.isError && <div role="alert" className="mx-auto max-w-6xl px-6 pb-3 text-sm text-danger">
      {apiErrorMessage(new ApiRequestError('LOGOUT_UNCONFIRMED', ''))} <Button variant="secondary" disabled={logout.isPending}
        onClick={logoutAction}>로그아웃 다시 시도</Button>
    </div>}
  </div>;
}
